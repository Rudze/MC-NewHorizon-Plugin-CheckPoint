# 🧭 Checkpoint

## 🎯 Description

**Checkpoint** est un plugin Spigot pour Paper 1.21.4 qui permet aux joueurs de définir un point de retour (checkpoint) et de s’y téléporter avec un délai de recharge de 30 minutes. Il s’intègre directement à l’API `DatabaseAPI` pour gérer la persistance via une base de données SQLite.

---

## 🗂️ Structure du plugin

| Fichier | Description |
|--------|-------------|
| `Main.java` | Classe principale du plugin. Contient la logique des commandes, la gestion du cooldown, et la persistance des checkpoints. |

---

## 🚀 Installation

1. Place `Checkpoint.jar` **et** `DatabaseAPI.jar` dans le dossier `/plugins` de ton serveur.
2. Démarre le serveur. Le plugin créera automatiquement une table `checkpoints` dans la base SQLite de `DatabaseAPI`.

---

## ⚙️ Commandes

| Commande | Description |
|---------|-------------|
| `/checkpoint set <pseudo>` | Enregistre la position actuelle du joueur donné comme checkpoint. |
| `/checkpoint tp <pseudo>` | Téléporte le joueur à son dernier checkpoint enregistré (cooldown de 30 minutes). |

---

## 🧠 Fonctionnement

- Les checkpoints sont enregistrés en base de données avec :
    - UUID du joueur
    - Monde, coordonnées (x, y, z), orientation (yaw, pitch)
- Le cooldown de 30 minutes est **par joueur**, et stocké en mémoire (non persistant après redémarrage).

---

## 🔗 Dépendance

Ce plugin dépend du plugin suivant :

```yaml
depend: [DatabaseAPI]
