package ca.qc.cvm.dba.persinteret.dao;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
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
					(filterText != null && !filterText.isEmpty() ? "WHERE a.name STARTS WITH $filterText " : "")
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
			//TODO : CHECK SI PERSONNE EXISTE DEJA (POUR MODIFICATION)

			Map<String, Object> params = new HashMap<String, Object>();
			//params.put("p1", person.getId());
			params.put("p1", person.getName());
			params.put("p2", person.getCodeName());
			params.put("p3", person.getDateOfBirth());
			params.put("p4", person.getStatus());
			params.put("p5", person.getConnexions());
			StatementResult result = session.run("CREATE (a:Person {name: $p1, codeName: $p2, dateOfBirth: $p3, status: $p4, connexions: $p5}) RETURN id(a) as id", params);

			if (result.hasNext()) {
				Record record = result.next();
				int keyInt = record.get("id").asInt();
				String key = String.valueOf(keyInt);

				// AJOUTER IMAGE:
				byte[] data = person.getImageData();

				DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
				DatabaseEntry theData = new DatabaseEntry(data);
				connection.put(null, theKey, theData);
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
		
		return success;
	}
	
	/**
	 * Suppression totale de toutes les donn�es du syst�me!
	 * 
	 * @return true si succ�s, false sinon
	 */
	public static boolean deleteAll() {
		boolean success = false;

		return success;
	}
	
	/**
	 * M�thode qui retourne le ratio de personnes en libert� par rapport au nombre total de fiches.
	 * 
	 * @return ratio entre 0 et 100
	 */
	public static int getFreeRatio() {
		int num = 0;
		
		return num;
	}
	
	/**
	 * Nombre de photos actuellement sauvegard�es dans le syst�me
	 * @return nombre
	 */
	public static long getPhotoCount() {
		return 0;
	}
	
	/**
	 * Nombre de fiches pr�sente dans la base de donn�es
	 * @return nombre
	 */
	public static long getPeopleCount() {
		return 0;
	}
		
	/**
	 * Permet de savoir la personne la plus jeune du syst�me
	 * 
	 * @return nom de la personne
	 */
	public static String getYoungestPerson() {
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
		
		return "--";
	}
	
	/**
	 * Permet de retourner, l'�ge moyen des personnes
	 * 
	 * @return par exemple, 20 (si l'�ge moyen est 20 ann�es arrondies)
	 */
	public static int getAverageAge() {
		int resultat = 0;
		return resultat;
	}
}
