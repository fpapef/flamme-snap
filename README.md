# Flamme Snap 🔥

**Appli Android qui remplit pour toi le formulaire officiel de Snapchat pour restaurer une flamme (Snapstreak).**

Tu tapes le pseudo de ton ami : l'appli ouvre le formulaire d'assistance de Snapchat et remplit tous les champs. Il ne te reste qu'à cocher le reCAPTCHA et à toucher Envoyer. L'appli n'envoie jamais rien à ta place.

![Aperçu de Flamme Snap (maquette)](docs/apercu.png)

## Fonctionnalités

- **Un ou plusieurs amis** : tape un pseudo, ou plusieurs (espaces ou virgules). L'appli fait une demande par ami, à la suite.
- **Remplissage automatique** du formulaire officiel de Snapchat, ouvert dans l'appli : les deux adresses e-mail, ton nom d'utilisateur, ton numéro, le type de conversation « 1:1 », le pseudo de ton ami et la description. Les champs sont repérés par leur libellé et l'appli attend qu'ils apparaissent.
- **Tu gardes la main** : c'est toi qui coches le reCAPTCHA et qui touches Envoyer. L'appli n'envoie jamais le formulaire à ta place.
- **Détection de l'envoi** : l'appli repère quand ta demande est partie et propose l'ami suivant.
- **Historique des demandes**, classées par jour (envoyées ou non confirmées), propre à chaque profil.
- **Amis récents** en un tap, **partage depuis Snapchat** (Partager → Flamme Snap) et bouton **Coller**.
- **Mes amis** : la liste de tous tes amis Snap, propre à chaque profil. Ajoute un pseudo, colle une liste, ou importe d'un coup l'export « Mes données » de Snapchat (fichier ZIP ou `friends.json`). Recherche, sélection de plusieurs amis, puis « Restaurer n flammes ».
- **Message en français ou en anglais**, et **mode sombre** automatique.
- **Profils** : un pour toi et autant que tu veux pour d'autres personnes. Chaque profil a ses infos, son message, ses amis, ses récents et son historique. Tout se gère dans Réglages.
- **Actions rapides** sur l'icône (appui long) : « Nouvelle demande » et « Mes demandes ».
- **Kit navigateur** : si le reCAPTCHA ne s'affiche pas, ouvre le formulaire dans ton navigateur et copie chaque info d'un geste.
- **Design épuré façon Apple** : police Inter, blanc pur, une seule couleur (celle de la flamme), flamme animée discrète, transitions façon iPhone.

## Installation

1. Télécharge le fichier `FlammeSnap-1.6.apk` de la dernière version dans [Releases](../../releases).
2. Ouvre-le sur le téléphone et autorise l'installation d'applis de source inconnue si Android le demande.
3. Au premier lancement, ouvre **Réglages** et complète ton profil : ton nom d'utilisateur Snapchat, ton e-mail et ton numéro avec l'indicatif du pays (par exemple +221).

Android 7 ou plus récent. Chaque nouvelle version s'installe par-dessus la précédente, sans perdre tes réglages.

## Utilisation

1. Tape le pseudo de ton ami (ou plusieurs) et touche **Continuer**.
2. Le formulaire de Snapchat s'ouvre et se remplit tout seul.
3. Vérifie, coche le reCAPTCHA, puis touche **Envoyer**.

Ou bien ouvre **Mes amis**, touche les amis concernés et **Restaurer n flammes** : une demande par ami, à la suite.

### Importer tous tes amis d'un coup

1. Dans Snapchat : **Paramètres › Mes données**.
2. Coche **Exporter les fichiers JSON**. Tu peux ne garder que la catégorie **Amis**, c'est plus léger.
3. Envoie la demande. Snapchat prépare le fichier et t'envoie un e-mail (de quelques heures à quelques jours).
4. Télécharge le fichier ZIP, puis dans Flamme Snap : **Mes amis › Importer depuis Snapchat**. Tu peux aussi ouvrir le ZIP directement avec Flamme Snap.

L'appli ne lit que la liste d'amis (pseudos et noms affichés) ; le reste du fichier est ignoré et rien ne quitte le téléphone.

## Versions

| Version | Nouveautés |
|---|---|
| [1.6](../../releases/tag/v1.6) | Mes amis : la liste de tous tes amis Snap, import de l'export Snapchat, restauration groupée |
| [1.5](../../releases/tag/v1.5) | Profils : moi et d'autres personnes, chacun avec son historique |
| [1.4](../../releases/tag/v1.4) | Bouton ⊗, aperçu des détails, actions rapides sur l'icône, détection d'envoi plus fiable |
| [1.3](../../releases/tag/v1.3) | Plusieurs amis à la suite, détection de l'envoi, historique, amis récents, mode sombre |
| [1.2](../../releases/tag/v1.2) | Design épuré façon Apple : police Inter, une seule couleur, transitions façon iPhone |
| [1.1](../../releases/tag/v1.1) | Motion design : flamme animée, transitions, carte d'état, confettis |
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

`tools/stdlib-fix` contient une version d'une classe de la bibliothèque Kotlin réécrite sans `invokedynamic`, que l'outil dx refuse pour Android 7 ; son comportement est identique à l'original.

## Structure

| Dossier | Contenu |
|---|---|
| `app/src` | Code Kotlin des écrans, des profils, de la liste d'amis, de l'import et de l'historique |
| `app/assets/fill.js` | Script qui remplit le formulaire dans la page |
| `app/res` | Écrans, couleurs (clair et sombre), animations, textes |
| `tools` | Assemblage de l'APK, icônes, installation des outils |

## Licences

La police [Inter](https://github.com/rsms/inter) est distribuée sous licence SIL Open Font License 1.1 (voir [`licences/Inter-OFL.txt`](licences/Inter-OFL.txt)).
