# Flamme Snap 🔥

**Appli Android qui remplit pour toi le formulaire officiel de Snapchat pour restaurer une flamme (Snapstreak).**

Tu tapes le pseudo de ton ami : l'appli ouvre le formulaire d'assistance de Snapchat et remplit tous les champs. Il ne te reste qu'à cocher le reCAPTCHA et à toucher Envoyer. L'appli n'envoie jamais rien à ta place.

## Fonctionnalités

- **Un seul champ** : tape le pseudo de ton ami et touche « Continuer ».
- **Remplissage automatique** du formulaire officiel de Snapchat, ouvert dans l'appli : les deux adresses e-mail, ton nom d'utilisateur, ton numéro, le type de conversation « 1:1 », le pseudo de ton ami et la description. Les champs sont repérés par leur libellé et l'appli attend qu'ils apparaissent.
- **Tu gardes la main** : c'est toi qui coches le reCAPTCHA et qui touches Envoyer. L'appli n'envoie jamais le formulaire à ta place.
- **Réglages** : ton pseudo, ton e-mail, ton numéro et le message envoyé à Snapchat.
- **Secours navigateur** : si le reCAPTCHA ne s'affiche pas, un bouton copie le pseudo et ouvre le formulaire dans ton navigateur.

## Installation

1. Télécharge le fichier `FlammeSnap-1.0.apk` de la dernière version dans [Releases](../../releases).
2. Ouvre-le sur le téléphone et autorise l'installation d'applis de source inconnue si Android le demande.
3. Au premier lancement, ouvre **Réglages** et entre : ton nom d'utilisateur Snapchat, ton e-mail et ton numéro avec l'indicatif du pays (par exemple +221).

Android 7 ou plus récent. Chaque nouvelle version s'installe par-dessus la précédente, sans perdre tes réglages.

## Utilisation

1. Tape le pseudo de ton ami et touche **Continuer**.
2. Le formulaire de Snapchat s'ouvre et se remplit tout seul.
3. Vérifie, coche le reCAPTCHA, puis touche **Envoyer**.

## Versions

| Version | Nouveautés |
|---|---|
| [1.0](../../releases/tag/v1.0) | Première version : remplissage automatique du formulaire, secours navigateur, réglages |

Le détail est dans [`CHANGELOG.md`](CHANGELOG.md).

## Confidentialité

Tes infos restent sur ton téléphone. Elles ne partent que vers Snapchat, par son propre formulaire, quand tu touches Envoyer. L'appli ne se connecte pas à ton compte Snapchat et ne demande aucun mot de passe.

Flamme Snap n'est pas lié à Snapchat ni à Snap Inc. : il remplit seulement le formulaire public d'assistance. La restauration d'une flamme reste décidée par Snapchat.

## Construire l'APK

Pas de Gradle : `build.sh` enchaîne aapt2, kotlinc 1.9.24, dx et uber-apk-signer. Il faut Java 17 ou plus récent, Python 3 avec Pillow (pour les icônes), `curl`, `zip` et `unzip`.

```
./tools/installer-outils.sh      # télécharge les outils (versions et empreintes vérifiées) dans ~/tools
KS_PASS=mot-de-passe ./build.sh  # build/FlammeSnap.apk
```

La clé de signature n'est pas publiée dans ce dépôt : `build.sh` utilise `~/flammesnap-release.jks` (ou la variable `KEYSTORE`) et en crée une s'il n'y en a pas. À chaque version publiée, le workflow [`apk.yml`](.github/workflows/apk.yml) construit l'APK signé et le joint à la version dans Releases.

## Structure

| Dossier | Contenu |
|---|---|
| `app/src` | Code Kotlin des écrans |
| `app/assets/fill.js` | Script qui remplit le formulaire dans la page |
| `app/res` | Écrans, couleurs, animations, textes |
| `tools` | Assemblage de l'APK, icônes, installation des outils |
