# SOC v4.0 -- Security Operations Center

<p align="center">
  <img src="https://img.shields.io/badge/Version-4.0-1303cf?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/OS-Windows%20%7C%20Linux-blue?style=for-the-badge" alt="OS">
  <img src="https://img.shields.io/badge/Licence-MIT-green?style=for-the-badge" alt="Licence">
</p>

Application de bureau Java (Swing) pour la **gestion d'incidents de cybersécurité**, destinée aux analystes d'un Security Operations Center (SOC). Interface sombre professionnelle thème **Violet / Vert Néon** avec fond dynamique selon la criticité de l'incident.

---

## Fonctionnalités

### Déclaration d'incident
- Formulaire complet : analyste, IP source, type, criticité, symptômes, description
- **8 types d'incidents** : Phishing, Malware, Intrusion, DDoS, Ransomware, Fuite de données, Accès non autorisé, Déni de service
- **3 niveaux de criticité** : Faible, Moyen, Critique
- **4 symptômes détectés** : Activité réseau suspecte, Tentatives de connexion échouées, Fichiers chiffrés, Processus inconnus
- **Fond dynamique** : l'interface change de couleur selon la criticité sélectionnée
- Validation visuelle des champs (bordures rouges en cas d'erreur)
- Validation de l'adresse IPv4 (format, valeurs 0-255, pas de zéros en tête)

### Historique & Horodatage
- Tableau avec **tri dynamique** sur toutes les colonnes
- **Recherche en temps réel** sur tous les champs
- **Cartes de statistiques** : Total, Critiques, Moyens, Faibles
- **Zone de détail** : affichage complet d'un incident sélectionné
- **Menu contextuel** (clic droit) : détail, modifier, supprimer, copier l'IP

### Gestion des données
- **Persistance automatique** : sauvegarde/chargement dans `~/.soc/incidents.dat`
- **Édition d'incidents** : double-clic pour modifier un incident existant
- **Suppression individuelle** : via menu contextuel ou touche Suppr
- **Export CSV** : format compatible Excel / tableurs
- **Export TXT** : rapport textuel structuré
- **Import** : charger des incidents depuis un fichier `.dat`

---

## Raccourcis clavier

| Raccourci | Action |
|---|---|
| `Ctrl + Entrée` | Signaler / Mettre à jour l'incident |
| `Ctrl + S` | Signaler / Mettre à jour l'incident |
| `Ctrl + R` | Réinitialiser le formulaire / Annuler l'édition |
| `Ctrl + F` | Focus sur la barre de recherche |
| `Echap` | Annuler l'édition / Désélectionner |
| `Suppr` | Supprimer l'incident sélectionné |

---

## Installation & Lancement

### Windows (sans Java installé)

Le package autonome inclut le JRE -- **aucune installation nécessaire**.

1. Extraire `SOC_Incident_Manager_v4_Windows.zip`
2. Double-cliquer sur `SOC_v4.bat`
3. C'est tout.

> **Prérequis** : Windows 64 bits.

### Windows / Linux (avec Java installé)

Java 17 minimum requis (Java 21 recommandé).

```bash
# Vérifier la version de Java
java -version

# Lancer l'application
java -jar SOCIncidentManager.jar
```

### Depuis le code source

```bash
# Compiler
javac -encoding UTF-8 SOCIncidentManager.java

# Lancer
java SOCIncidentManager
```

---

## Structure du projet

```
SOCIncidentManager.java   # Code source complet (~1880 lignes, 1 fichier)
SOCIncidentManager.jar    # Application compilée (JAR exécutable)
```

### Architecture technique

- **Single-file Java application** (Swing, AWT, java.time)
- **Aucune dépendance externe** (ni Maven, ni Gradle, ni librairie tierce)
- **Persistance** : fichier binaire propriétaire (séparateur ASCII `0x1E`) dans `~/.soc/`
- **Look & Feel** : Nimbus (multiplateforme)
- **Encodage** : UTF-8

---

## Types d'incidents supportés

| Type | Description |
|---|---|
| Phishing | Tentative d'hameçonnage par e-mail ou site frauduleux |
| Malware | Logiciel malveillant détecté sur un poste ou serveur |
| Intrusion | Accès non autorisé à un système |
| DDoS | Attaque par déni de service distribué |
| Ransomware | Logiciel de rançon chiffrant les fichiers |
| Fuite de données | Exfiltration de données sensibles |
| Accès non autorisé | Connexion avec des identifiants non légitimes |
| Déni de service | Saturation d'un service ou d'un serveur |
| Autre | Tout autre type d'incident |

---

## Captures d'écran

### Onglet Déclaration

Le formulaire permet de saisir un nouvel incident avec :
- Le nom de l'analyste et l'adresse IP source (champs obligatoires)
- Le type d'incident et le niveau de criticité
- Les symptômes détectés (au moins un requis)
- Une description détaillée (optionnelle mais recommandée)
- Le fond de l'interface change selon la criticité sélectionnée (rouge = critique, jaune = moyen, vert = faible)

### Onglet Historique

- **4 cartes de statistiques** en haut (Total, Critiques, Moyens, Faibles)
- **Barre de recherche** pour filtrer les incidents en temps réel
- **Tableau triable** avec coloration de la colonne Criticité
- **Zone de détail** en bas avec affichage complet de l'incident sélectionné
- **Boutons** : Importer, Vider, Export CSV, Export TXT

---

## Criticité -- Indicateurs visuels

| Niveau | Couleur du fond | Ligne d'en-tête | Description |
|---|---|---|---|
| **Critique** | Rouge sombre | Rouge | Intervention immédiate requise |
| **Moyen** | Jaune / Ambre sombre | Jaune | Impact modéré -- surveillance renforcée |
| **Faible** | Vert sombre | Vert | Faible impact -- suivi standard |
| **Aucune** | Violet sombre (défaut) | Violet | Aucun incident en cours |

---

## Données utilisateur

Les incidents sont sauvegardés automatiquement dans :

| OS | Chemin |
|---|---|
| Windows | `C:\Users\<utilisateur>\.soc\incidents.dat` |
| Linux | `/home/<utilisateur>/.soc/incidents.dat` |
| macOS | `/Users/<utilisateur>/.soc/incidents.dat` |

Pour réinitialiser : supprimer le dossier `.soc` de votre répertoire personnel.

---

## Versionnage

| Version | Date | Changements |
|---|---|---|
| **4.0** | 2026 | Persistance auto, recherche, tri, édition, suppression, export CSV/TXT, import, raccourcis, validation renforcée, barre de statut, tooltips, menu contextuel |
| 3.0 | -- | Ajout de l'onglet Historique & Horodatage |
| 2.0 | -- | Thème Violet / Vert Néon, fond dynamique par criticité |
| 1.0 | -- | Première version -- formulaire de déclaration basique |

---

## Auteur

Application développée en Java pur (Swing) pour un usage pédagogique et professionnel dans le domaine de la cybersécurité.

---

## Licence

Ce projet est fourni à titre éducatif. Libre d'utilisation, de modification et de distribution.
