
# Ordonnancement de Workflows

Ce projet fournit un **cadre de simulation pour l’ordonnancement de workflows**, intégrant plusieurs algorithmes métaheuristiques et des jeux de données configurables.

---

## Prérequis

* **Java** : OpenJDK **21.0.9 LTS** (Temurin)

  ```bash
  openjdk version "21.0.9" 2025-10-21 LTS
  OpenJDK Runtime Environment Temurin-21.0.9+10 (build 21.0.9+10-LTS)
  ```

  > Recommandé : installation via **SDKMAN!**

* **Maven** :  3.6.3

---

## Démarrage

### 1. Cloner le dépôt

```bash
git clone https://github.com/LIMAMMohamedlimam/workflow_scheduling.git
cd workflow_scheduling
```

### 2. Construire le projet

```bash
mvn clean package
```

---

## Lancer la simulation

### Exécution par défaut

```bash
mvn exec:java -Dexec.mainClass="org.example.CustomCostSimulation"
```

### Exécution avec arguments

```bash
mvn exec:java -Dexec.mainClass="org.example.CustomCostSimulation" \
  -Dexec.args="task40 WOA false"
```

---

## Arguments en ligne de commande

| Index | Argument                | Description                     | Valeurs autorisées                                                        |
| ----: | ----------------------- | ------------------------------- | ------------------------------------------------------------------------- |
|     0 | Jeu de données          | Taille du dataset de workflow   | `task40`, `task80`, `task120`, `task160`, `task200`, `task240`, `task280` |
|     1 | Algorithme              | Algorithme d’ordonnancement     | `SA_MultiObjective`, `MOWOA`, `WOASA`, `WOARDA`, `RDA`                    |
|     2 | Génération dataset eval | Générer le dataset d’évaluation | `true`, `false`                                                           |

---

## Exemple

```bash
mvn exec:java -Dexec.mainClass="org.example.CustomCostSimulation" \
  -Dexec.args="task120 WOASA true"
```

Cette commande exécute l’algorithme hybride **WOA + SA** sur le dataset `task120` et génère un jeu de données d’évaluation.

---

## Remarques

* Les noms des datasets et des algorithmes sont **sensibles à la casse**.
* Les fichiers de sortie (résultats et jeux de données d’évaluation) sont générés dans le répertoire de sortie du projet.

---


