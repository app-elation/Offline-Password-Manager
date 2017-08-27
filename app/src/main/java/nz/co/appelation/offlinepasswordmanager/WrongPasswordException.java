package nz.co.appelation.offlinepasswordmanager;

/**
 * Thrown while decrypting the password database, if the MAGIC header number doesn't match... indicating failed decryption.
 */
public class WrongPasswordException extends Exception {

}
