package de.farberg.apollo.jaas;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;

import javax.security.auth.login.LoginException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtpasswdFile {
	private static final Logger log = LoggerFactory.getLogger(HtpasswdFile.class);

	private String passwordFilePath;

	public HtpasswdFile(String passwordFilePath) {
		this.passwordFilePath = passwordFilePath;
	}

	/**
	 * Authenticates the user against the htpasswd file.
	 *
	 * @param user
	 *            user to authentication
	 * @param password
	 *            user's password
	 * @return true if the user is authenticated
	 * @throws LoginException
	 *             thrown if the user is not authenticated
	 */
	protected boolean authenticate(String user, char[] password) throws LoginException {
		log.debug("Attempting to authenticate {} against htpasswd file {}", user, passwordFilePath);
		BufferedReader htpasswdReader = null;
		try {
			htpasswdReader = new BufferedReader(new FileReader(passwordFilePath));
			String[] entryParts;

			for (String passwordEntry = htpasswdReader.readLine(); passwordEntry != null; passwordEntry = htpasswdReader.readLine()) {
				entryParts = passwordEntry.split(":");
				String htpasswdUsername = entryParts[0];
				String htpasswdPassword = entryParts[1];

				if (htpasswdUsername.equals(user)) {
					log.debug("Found line for user: {}", user);

					// TODO Improve this (cf.
					// https://github.com/gitblit/gitblit/blob/master/src/main/java/com/gitblit/auth/HtpasswdAuthProvider.java)

					if (htpasswdPassword.startsWith("{SHA}")) {
						log.debug("Validating SHA-1 password for user {}", user);
						return validateSHA1Password(password, htpasswdPassword);

					} else if (htpasswdPassword.matches("^\\$.*\\$.*$")) {
						log.debug("Validating MD5 password for user {}", user);
						return validateMD5Password(password, htpasswdPassword);

					} else
						throw new LoginException("Unsupported entry type, use htpasswd with the -s for SHA option");

				}
			}

			log.debug("Unable to authenticate user {}", user);
			throw new LoginException("Unable to authenticate user");
		} catch (IOException e) {
			log.error("Unable to read password file", e);
			throw new LoginException(e.getMessage());
		} finally {
			try {
				htpasswdReader.close();
			} catch (IOException e) {

			}
		}
	}

	/**
	 * Checks if the given password matches the MD5 hashed valid password.
	 *
	 * @param givenPassword
	 *            given password
	 * @param validPassword
	 *            MD5 hashed valid password
	 * @return true if the password is valid
	 * @throws LoginException
	 *             thrown if the given password does not match the valid password
	 */
	protected boolean validateMD5Password(char[] givenPassword, String validPassword) throws LoginException {
		throw new LoginException("MD5 passwords are not implemented");
	}

	/**
	 * Checks if the given password matches the SHA1 hashed valid password.
	 *
	 * @param givenPassword
	 *            given password
	 * @param validPassword
	 *            MD5 hashed valid password
	 * @return true if the password is valid
	 * @throws LoginException
	 *             thrown if the given password does not match the valid password
	 */
	protected boolean validateSHA1Password(char[] givenPassword, String validPassword) throws LoginException {
		validPassword = validPassword.replace("{SHA}", "");

		MessageDigest sha1Digest = DigestUtils.getSha1Digest();
		byte[] passwordBytes = new String(givenPassword).getBytes();

		String computedDigest = new String(Base64.encodeBase64(sha1Digest.digest(passwordBytes)), Charset.forName("UTF-8"));
		boolean valid = validPassword.equals(computedDigest);
		log.debug("SHA validation, match: {}", valid);

		return valid;
	}
}
