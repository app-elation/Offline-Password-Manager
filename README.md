# Offline Password Manager
As the name suggests, this Android app is a password manager, and it's main feature is that it has no online capabilities - your passwords are saved only on your device.

If you are concerned about having your passwords in the cloud, on some company's server, or in some [hackable](https://www.gizmodo.com.au/2015/06/lastpass-defender-of-our-passwords-just-got-hacked/) [honey-pot](http://www.zdnet.com/article/onelogin-hit-by-data-breached-exposing-sensitive-customer-data/), then this app might be for you. 

If you worry about [secret coercion](https://en.wikipedia.org/wiki/Lavabit), or data sovereignty, or you've heard of password managers [leaking](https://www.theregister.co.uk/2017/03/21/lastpass_vulnerabilities/), or worry about the [flood of cracked passwords](http://bgr.com/2017/05/18/password-leak-hack-master-list/) [eroding](https://arstechnica.com/information-technology/2013/05/how-crackers-make-minced-meat-out-of-your-passwords/) the security of the master password for your online password DB, then this app might be for you.


## Features
* No online capability, passwords saved only to your device
* No analytics or tracking
* No ads
* App requests NO permissions
* Open source
* Encrypted password DB
* Quick fingerprint access
* Import / Export to CSV file
* Import / Export to encrypted file
* Prevents screenshots
* Specifically disables (only for this app) Google's *app-backup* function to prevent file and settings leaving the device
* Flexible account categorisation
* Auto-logout function
* Account search
* Password generator
* Clipboard integration (disabled by default)
* Multi-function context menus
* Runs on Android 6 Marshmallow and up

<a href='https://play.google.com/store/apps/details?id=nz.co.appelation.offlinepasswordmanager&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png'/></a>

## Code Reviewers
If you are here to review the code, welcome!
This application has a mostly flat package structure, lots of comments and references to topics on cryptography.
In an application like this - trust is very important. That is the very reason why I am releasing the the source for this project.
Some object composition may eschews conventional wisdom - this was done in favour of readability. Some of the Android examples this app was built on, are pretty [complicated](https://github.com/googlesamples/android-FingerprintDialog/issues/12) and could deter casual code reviewers.

## Cryptography & Security Features
This application only uses standard cryptographic libraries as supplied by Android.
Optional fingerprint authentication makes use of the Android secure keystore.

This app uses AES symmetric key encryption in the following schemes:

The user's Master Password (8 chars / 64 bit or more) is hashed with a 512 bit secure-random generated salt in a 10000 iteration PBKDF2WithHmacSHA1 algorithm to produce a 256 bit key (Key-a).

The salt is persisted in the app's private storage area, in the clear.

Key-a is then used to encrypt the password DB. A new initialization vector (IV-a) is also derived through standard library and persisted in the clear, as a header to the encrypted password DB.

If user opts not to use fingerprint auth:
When authenticating, the user supplies the Master Password. The salt is read from file, and Key-a is then again computed with PBKDF2WithHmacSHA1 over 10000 iterations, and used to decrypt the password DB.

If user opts to use fingerprint auth:
A key (Key-b) is allocated in the Android secure keystore (AES/CBC/PKCS7Padding, 256 bit). This key is set to require fingerprint authentication (Android managed). A cipher is prepared for encryption with Key-b, and a 128 bit initialization vector (iv-b) is derived through standard library and persisted in the clear.
Key-a is then encrypted with the fingerprint-authenticated Key-b, and IV-b, and persisted in the App's private storage area.

When authenticating with fingerprint, Key-b together with the persisted IV-b, is used to initialise a cipher for decryption. This cipher is then used to decrypt the persisted, encrypted Key-a. Once Key-a is decrypted, it is used, along with IV-a to decrypt the password-DB.

![](https://github.com/app-elation/Offline-Password-Manager/blob/master/misc/scheme.png)

## //TODO:
* Create a help page with instructions
* Improve error message for CSV imports from invalid file types
* Add key-listener to account listing page, to auto-search when you type (for devices with hardware keyboards)
* Add alphabetical index to account listing page
* Fix application heading font size in landscape mode
* Possibly add web-view for opening account pages, inject username & password
* Possibly add service to auto-clear clipboard
* Make auto-logout time configurable

## Support
This app was designed to be as straight forward as possible. Most of the settings options have generous summary text to explain.
If you really have a question, create an Issue against this github project, and I'll try to help!

## License
This app is distributed under the MIT license.
