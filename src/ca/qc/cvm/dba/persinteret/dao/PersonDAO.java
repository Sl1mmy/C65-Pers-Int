package ca.qc.cvm.dba.persinteret.dao;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import com.sleepycat.je.*;
import org.bson.Document;
import org.bson.types.Binary;

import com.mongodb.Block;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import ca.qc.cvm.dba.persinteret.entity.Person;
import org.neo4j.driver.Session;
import org.neo4j.driver.StatementResult;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import javax.xml.crypto.Data;

public class PersonDAO {

/**
	 * M�thode permettant de retourner la liste des personnes de la base de donn�es.
	 * 
	 * Notes importantes:
	 * - N'oubliez pas de limiter les r�sultats en fonction du param�tre limit
	 * - La liste doit �tre tri�es en ordre croissant, selon les noms des personnes
	 * - Le champ de filtre doit permettre de filtrer selon le pr�fixe du nom (insensible � la casse)
	 * - Si le champ withImage est � false, alors l'image (byte[]) n'a pas � faire partie des r�sultats
	 * - N'oubliez pas de mettre l'ID dans la personne, car c'est utile pour savePerson()
	 * - Il pourrait ne pas y avoir de filtre (champ filtre vide)
	 *  
	 * @param filterText champ filtre, peut �tre vide ou null
	 * @param withImage true si l'image est n�cessaire, false sinon.
	 * @param limit permet de restreindre les r�sultats
	 * @return la liste des personnes, selon le filtre si n�cessaire et filtre
	 */
	public static List<Person> getPeopleList(String filterText, boolean withImage, int limit) {
		final List<Person> peopleList = new ArrayList<Person>();

		Session session = Neo4jConnection.getConnection();
		Database connection = BerkeleyConnection.getConnection();

		try {
			//query
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("filterText", filterText != null ? filterText.toLowerCase() : "");
			params.put("limit", limit);

			String query = String.format(
					"MATCH (a:Person) %sRETURN a.name AS name, a.codeName AS codeName, a.status AS status, a.dateOfBirth AS dob, "
							+ "a.connexions AS connexions, a.imageData AS imageData, id(a) AS id "
							+ "ORDER BY a.name ASC LIMIT $limit",
					(filterText != null && !filterText.isEmpty() ? "WHERE toLower(a.name) STARTS WITH $filterText " : "")
			);

			StatementResult result = session.run(query, params);

			while (result.hasNext()) {
				Record record = result.next();
				List<String> connexions = record.get("connexions").asList(Value::asString);

				byte[] retData = null;
				if(withImage) {
					String key = String.valueOf(record.get("id").asInt());
					DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
					DatabaseEntry theData = new DatabaseEntry();

					if (connection.get(null, theKey, theData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						retData = theData.getData();
					}
					//doesnt exist
				}


				Person person = new Person(
						String.valueOf(record.get("id").asInt()),
						record.get("name").asString(),
						record.get("codeName").asString(),
						record.get("status").asString(),
						record.get("dob").asString(),
						connexions,
						retData
				);

				peopleList.add(person);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return peopleList;
	}

	/**
	 * M�thode permettant de sauvegarder une personne
	 * 
	 * Notes importantes:
	 * - Si le champ "id" n'est pas null, alors c'est une mise � jour, pas une insertion
	 * - Le nom de code est optionnel, le reste est obligatoire
	 * - Le nom de la personne doit �tre unique
	 * - Regarder comment est fait la classe Personne pour avoir une id�e des donn�es � sauvegarder
	 * - Pour cette m�thode, d�normaliser pourrait vous �tre utile. La performance des lectures est vitale.
	 * - Je vous conseille de sauvegarder la date de naissance en format long (en millisecondes)
	 * - Une connexion va dans les deux sens. Plus pr��is�ment, une personne X qui connait une autre personne Y
	 *   signifie que cette derni�re connait la personne X
	 * - Attention, les connexions d'une personne pourraient changer avec le temps (ajout/suppression)
	 * - N'oubliez pas de sauvegarder votre image dans BerkeleyDB
	 * 
	 * @param person
	 * @return true si succ�s, false sinon
	 */
	public static boolean save(Person person) {
		boolean success = false;
		Session session = Neo4jConnection.getConnection();
		Database connection = BerkeleyConnection.getConnection();
		try {
			String nodeId = person.getId(); // Assuming getId() fetches the internal Neo4j ID

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("p1", person.getName());
			params.put("p2", person.getCodeName());
			params.put("p3", person.getDateOfBirth());
			params.put("p4", person.getStatus());
			params.put("p5", person.getConnexions());

			String cypherQuery;
			if (nodeId != null) {
				// Node exists, update it
				int nodeIdInt = Integer.parseInt(nodeId);
				cypherQuery = "MATCH (a) WHERE id(a) = $nodeId " +
						"SET a.name = $p1, a.codeName = $p2, a.dateOfBirth = $p3, a.status = $p4, a.connexions = $p5 " +
						"RETURN id(a) as id";
				params.put("nodeId", nodeIdInt);
			} else {
				// Node does not exist, create it
				cypherQuery = "CREATE (a:Person {name: $p1, codeName: $p2, dateOfBirth: $p3, status: $p4, connexions: $p5}) " +
						"RETURN id(a) as id";
			}
			StatementResult result = session.run(cypherQuery, params);
			int keyInt = 0;
			if (result.hasNext()) {
				Record record = result.next();
				keyInt = record.get("id").asInt();
				String key = String.valueOf(keyInt);

				// AJOUTER IMAGE:
				byte[] data = person.getImageData();

				DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
				DatabaseEntry theData = new DatabaseEntry(data);
				connection.put(null, theKey, theData);
			}
			params.put("nodeId", keyInt);
			session.run("MATCH (a:Person)-[r:CONNEXION]->(b:Person) WHERE id(a) = $nodeId DELETE r",params);
			if (!person.getConnexions().isEmpty()) {
				for (String connName : person.getConnexions()) {
					params.put("connName", connName);
					session.run("MATCH (a:Person), (b:Person) WHERE id(a) = $nodeId AND b.name = $connName "
									+ "MERGE (a)-[:CONNEXION]->(b)",params);
				}
			}

			success = true;
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return success;
	}
	
	/**
	 * Suppression des donn�es/fiche d'une personne
	 * 
	 * @param person
	 * @return true si succ�s, false sinon
	 */
	public static boolean delete(Person person) {
		boolean success = false;
		Session session = Neo4jConnection.getConnection();
		Database connection = BerkeleyConnection.getConnection();
		try {
			String nodeId = person.getId();

			if (nodeId != null) {
				// Delete the node and all its connected relationships
				int nodeIdInt = Integer.parseInt(nodeId);
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("nodeId", nodeIdInt);
				session.run("MATCH (a:Person) WHERE id(a) = $nodeId DETACH DELETE a", params);
				DatabaseEntry theKey = new DatabaseEntry(nodeId.getBytes("UTF-8"));
				connection.delete(null, theKey);

				success = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return success;
	}
	
	/**
	 * Suppression totale de toutes les donn�es du syst�me!
	 * 
	 * @return true si succ�s, false sinon
	 */
	public static boolean deleteAll() {
		boolean success = false;
		Session session = Neo4jConnection.getConnection();
		Database connection = BerkeleyConnection.getConnection();

		Cursor myCursor = null;
		Transaction txn = null;

		try {
			txn = connection.getEnvironment().beginTransaction(null, null);
			myCursor = connection.openCursor(txn, null);
			try{
			DatabaseEntry foundKey = new DatabaseEntry();
			DatabaseEntry foundData = new DatabaseEntry();

			while (myCursor.getNext(foundKey, foundData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				myCursor.delete();  // Delete each record one by one
			}
			// Delete all
		} finally {
			myCursor.close();  // Ensure the cursor is closed within the try-finally block
		}

			txn.commit();
			session.run("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r");
			success = true;
		} catch (Exception e) {
			if (txn != null) {
				txn.abort(); // Abort the transaction on error
			}
			e.printStackTrace();
		} finally {

		}

		return success;
	}
	
	/**
	 * M�thode qui retourne le ratio de personnes en libert� par rapport au nombre total de fiches.
	 * 
	 * @return ratio entre 0 et 100
	 */
	public static int getFreeRatio() {
		Session session = Neo4jConnection.getConnection();
		try {
			// Exécutez une requête pour compter le total de personnes et celles en liberté
			StatementResult result = session.run("MATCH (p:Person) RETURN count(p) as total, sum(CASE WHEN p.status = 'Libre' THEN 1 ELSE 0 END) as libre");

			if (result.hasNext()) {
				Record record = result.next();
				int total = record.get("total").asInt();
				int libre = record.get("libre").asInt();

				// Pour éviter la division par zéro
				if (total > 0) { // Pour éviter la division par zéro
					return (int) Math.round(100.0 * libre / total); // Calculer le pourcentage puis arrondir
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return 0;
	}
	
	/**
	 * Nombre de photos actuellement sauvegard�es dans le syst�me
	 * @return nombre
	 */
	public static long getPhotoCount() {
		int photoCount = 0;
		Cursor myCursor = null;
		Database connection = BerkeleyConnection.getConnection();
		try {
			myCursor = connection.openCursor(null, null);
			DatabaseEntry foundKey = new DatabaseEntry();
			DatabaseEntry foundData = new DatabaseEntry();
			while (myCursor.getNext(foundKey, foundData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				photoCount++;
			}
		} finally {
			if (myCursor != null) {
				myCursor.close();
			}
		}

		return photoCount;
	}
	
	/**
	 * Nombre de fiches pr�sente dans la base de donn�es
	 * @return nombre
	 */
	public static long getPeopleCount() {

		Session session = Neo4jConnection.getConnection();
		try {
			// Exécutez une requête pour compter le total de personnes et celles en liberté
			StatementResult result = session.run("MATCH (p:Person) RETURN count(p) as total");

			if (result.hasNext()) {
				Record record = result.next();
				int total = record.get("total").asInt();

				// Pour éviter la division par zéro
				if (total > 0) { // Pour éviter la division par zéro
					return total; // Calculer le pourcentage puis arrondir
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
		
	/**
	 * Permet de savoir la personne la plus jeune du syst�me
	 * 
	 * @return nom de la personne
	 */
	public static String getYoungestPerson() {
		Session session = Neo4jConnection.getConnection();
		try {
			// Exécutez une requête pour compter le total de personnes et celles en liberté
			StatementResult result = session.run("MATCH (p:Person) RETURN p.name AS name ORDER BY p.dateOfBirth DESC LIMIT 1");

			if (result.hasNext()) {
				Record record = result.next();
				return record.get("name").asString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "--";
	}
	
	/**
	 * Afin de savoir la prochaine personne � investiguer,
	 * Il faut retourner la personne qui connait, ou est connu, du plus grand nombre de personnes 
	 * disparues ou d�c�d�es (morte). Cette personne doit �videmment avoir le statut "Libre"
	 * 
	 * @return nom de la personne
	 */
	public static String getNextTargetName() {
		Session session = Neo4jConnection.getConnection();
		try {
			// Exécutez une requête pour compter le total de personnes et celles en liberté
			StatementResult result = session.run("MATCH (p:Person {status: 'Libre'})-[:CONNEXION]-(m:Person) " +
					"WHERE m.status IN ['Disparu', 'Mort'] " +
					"WITH p, COUNT(m) AS connectedCount " +
					"RETURN p.name AS name, connectedCount " +
					"ORDER BY connectedCount DESC LIMIT 1");
			if (result.hasNext()) {
				Record record = result.next();
				return record.get("name").asString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "--";
	}
	
	/**
	 * Permet de retourner, l'�ge moyen des personnes
	 * 
	 * @return par exemple, 20 (si l'�ge moyen est 20 ann�es arrondies)
	 */
	public static int getAverageAge() {
		int resultat = 0;
		Session session = Neo4jConnection.getConnection();
		try {
			StatementResult result = session.run("MATCH (p:Person) WHERE p.dateOfBirth IS NOT NULL " +
					"RETURN round(avg(date().year - date(p.dateOfBirth).year)) AS averageAge");
			if (result.hasNext()) {
				Value value = result.single().get("averageAge");
				if (!value.isNull()) {
					resultat = value.asInt();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultat;
	}
}
