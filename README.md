# Projet "Personnes d'intérêt"
### Équipe :
- Noé Bousquet 
- Maxime Desrochers 

### Choix de type de BD :

Nous avons choisi d'utiliser **Neo4j** comme base de données pour notre projet. 
Neo4j est une base de données orientée graphe, ce qui signifie qu'elle est particulièrement adaptée pour gérer des données interconnectées, comme les relations entre personnes. Dans notre système, nous avons besoin de pouvoir lier des personnes entre elles pour indiquer qu'elles se connaissent. En effet, Neo4j offre des fonctionnalités puissantes pour gérer ce type de relations. De plus, Neo4j est bien adapté pour effectuer des recherches et des analyses sur les données existantes, ce qui correspond parfaitement à nos besoins. Grâce à Neo4j nous pouvons facilement trouver toutes les personnes liées à une personne donnée, ou détecter des motifs dans le graphe des relations entre personnes. 

En résumé, Neo4j offre les fonctionnalités dont nous avons besoin pour notre projet, tout en étant optimisée pour gérer des données interconnectées, ce qui en fait un choix idéal pour notre système. 

### Collections/libellés :
 nous utiliserons les libellés suivants : 
- **Person** : pour représenter une personne 
- **Connexion** : pour représenter les relations entre personnes

### Indexes: 
- **Person - Status** : Pour les requêtes concernant le status de la personne (pourcentage en liberté / fiche)
- **Person - DateOfBirth** : Pour les requêtes concernant l'age d'une personne (la plus jeune / age moyen / fiche)
- **Connexions entre personnes**: Pour les requêtes concernant les connexions entre personnes (personne cible / fiche)