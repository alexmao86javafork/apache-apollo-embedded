package apollomin.jaas;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.apache.activemq.jaas.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAAS login module that reads an Apache HTTPD htpasswd formatted file for its authentication information.
 * <p/>
 * This login module requires an option called "htpasswdFile" which is the location of the htpasswd file that will be used. The provided
 * login handler must also be an instance of {@link NameCallback} and {@link PasswordCallback}.
 * <p/>
 * The password is not stored within the Subject's credential sets.
 */
public class HtpasswdLoginModule implements LoginModule {
	private final Logger log = LoggerFactory.getLogger(HtpasswdLoginModule.class);

	public final static String HTPASSWD_FILE_OPTION = "htpasswdFile";

	private Subject subject;

	private CallbackHandler callbackHandler;

	private HtpasswdFile passwordFile;

	private Principal principal;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
		this.subject = subject;
		this.callbackHandler = callbackHandler;

		passwordFile = new HtpasswdFile((String) options.get(HTPASSWD_FILE_OPTION));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean login() throws LoginException {
		NameCallback nameCallback = new NameCallback("Enter user name: ");
		PasswordCallback passwordCallback = new PasswordCallback("Enter password: ", false);
		principal = null;

		try {

			if (callbackHandler != null)
				callbackHandler.handle(new Callback[] { nameCallback, passwordCallback });

		} catch (IOException e) {
			log.error("Unable to process callback: " + e, e);
			throw new LoginException(e.toString());
		} catch (UnsupportedCallbackException e) {
			log.error("Unsupported callback:" + e, e);
			throw new LoginException(e.toString());
		}

		boolean authenticationValid = passwordFile.authenticate(nameCallback.getName(), passwordCallback.getPassword());
		log.debug("Login " + (authenticationValid ? "" : "in") + "valid for username " + nameCallback.getName());

		if (authenticationValid) {
			principal = new UserPrincipal(nameCallback.getName());
		}

		return authenticationValid;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean commit() throws LoginException {
		log.debug("Committing login transaction");
		subject.getPrincipals().add(principal);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean logout() throws LoginException {
		subject.getPrincipals().remove(principal);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean abort() throws LoginException {
		subject.getPrincipals().remove(principal);
		return true;
	}

}