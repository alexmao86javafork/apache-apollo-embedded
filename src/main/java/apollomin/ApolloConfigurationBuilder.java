package apollomin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.activemq.apollo.dto.AcceptingConnectorDTO;
import org.apache.activemq.apollo.dto.AccessRuleDTO;
import org.apache.activemq.apollo.dto.AddUserHeaderDTO;
import org.apache.activemq.apollo.dto.AuthenticationDTO;
import org.apache.activemq.apollo.dto.BrokerDTO;
import org.apache.activemq.apollo.dto.QueueDTO;
import org.apache.activemq.apollo.dto.VirtualHostDTO;
import org.apache.activemq.apollo.dto.WebAdminDTO;
import org.apache.activemq.apollo.stomp.dto.StompDTO;
import org.apache.activemq.jaas.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApolloConfigurationBuilder {

	private static final Logger log = LoggerFactory.getLogger(ApolloConfigurationBuilder.class);

	private String jaasConfigFile = null;
	private boolean enableAlwaysTrueAuthorization = false;
	private boolean enableLocalhostAccess = true;
	private boolean enableExternalAccess = false;
	private int webAdminPort = -1;
	private int defaultMaxConnectionLimit = 100;
	private Collection<QueueDTO> queues = new LinkedList<>();
	private Collection<AcceptingConnectorDTO> connectors = new LinkedList<>();
	private Collection<AddUserHeaderDTO> addUserHeaders = new ArrayList<>();

	public ApolloConfigurationBuilder jaasAuthentication(String jaasConfigFile) {
		this.jaasConfigFile = jaasConfigFile;
		return this;
	}

	public ApolloConfigurationBuilder alwaysTrueAuthorization() {
		this.enableAlwaysTrueAuthorization = true;
		return this;
	}

	public ApolloConfigurationBuilder localhostAccessOnly() {
		this.enableLocalhostAccess = true;
		this.enableExternalAccess = false;
		return this;
	}

	public ApolloConfigurationBuilder externalAccess() {
		this.enableLocalhostAccess = true;
		this.enableExternalAccess = true;
		return this;
	}

	public ApolloConfigurationBuilder queueNonPersistentNoExpiration(String queueName) throws Exception {
		return queue(queueName, false, 0);
	}

	public ApolloConfigurationBuilder queuePersistentNoExpiration(String queueName) throws Exception {
		return queue(queueName, true, 0);
	}

	public ApolloConfigurationBuilder queue(String queueName, boolean persistent, int autoDeleteAfter) throws Exception {
		QueueDTO queue = new QueueDTO();
		queue.persistent = persistent;
		queue.auto_delete_after = autoDeleteAfter;
		queue.id = queueName;
		this.queues.add(queue);
		return this;
	}

	// / Fires up the web admin console on HTTP.
	public ApolloConfigurationBuilder webAdmin(int port) {
		this.webAdminPort = port;
		return this;
	}

	/**
	 * This adds support for message sender identification (cf. http://activemq.apache.org/apollo/documentation/stomp-manual.html) This DTO
	 * is added to the individual transport configurations TODO Maybe add <add_user_header
	 * kind="org.apache.activemq.apollo.broker.security.SourceAddressPrincipal">sender-ip</add_user_header>
	 * 
	 * Call after enabling a transport
	 * 
	 * @param headerName
	 * @return
	 */
	public ApolloConfigurationBuilder authenticatedHeader(String headerName) {
		AddUserHeaderDTO authenticatedUserHeader = new AddUserHeaderDTO();
		authenticatedUserHeader.kind = UserPrincipal.class.getCanonicalName();
		authenticatedUserHeader.name = headerName;

		this.addUserHeaders.add(authenticatedUserHeader);

		return this;
	}

	private AcceptingConnectorDTO defaultAcceptingConnectorDTO(String id, String protocolPrefix, int port) {
		AcceptingConnectorDTO connector = new AcceptingConnectorDTO();
		connector.id = id;
		connector.bind = protocolPrefix + "://0.0.0.0:" + port;
		connector.connection_limit = defaultMaxConnectionLimit;
		return connector;
	}

	public ApolloConfigurationBuilder stomp(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("stomp@" + port, "tcp", port);

		StompDTO stompDto = new StompDTO();
		connector.protocols.add(stompDto);

		this.connectors.add(connector);
		return this;
	}

	public ApolloConfigurationBuilder stompOverWebsocket(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("websockets-stomp@" + port, "ws", port);

		StompDTO stompDto = new StompDTO();
		connector.protocols.add(stompDto);

		this.connectors.add(connector);
		return this;
	}

	public ApolloConfigurationBuilder amqp(int port) {
		throw new RuntimeException("Not implemented yet.");
	}

	public ApolloConfigurationBuilder mqtt(int port) {
		throw new RuntimeException("Not implemented yet.");
	}

	public ApolloConfigurationBuilder openWire(int port) {
		throw new RuntimeException("Not implemented yet.");
	}

	public ApolloConfigurationBuilder defaultMaxConnectionLimit(int limit) {
		this.defaultMaxConnectionLimit = 5000;
		return this;
	}

	public BrokerDTO build() {
		log.info("Starting embedded message queuing broker...");

		// Create the configuration
		BrokerDTO brokerConfiguration = new BrokerDTO();

		// Authentication settings
		if (jaasConfigFile != null) {
			log.info("Enabling security on the broker using JAAS file {}", jaasConfigFile);

			// Set the location of the JAAS configuration file accordingly
			System.setProperty("java.security.auth.login.config", jaasConfigFile);

			// Enable authentication
			brokerConfiguration.authentication = new AuthenticationDTO();
			brokerConfiguration.authentication.enabled = true;
			brokerConfiguration.authentication.domain = "Internal";

		} else {
			log.warn("Not using authentication/autorization. This configuration is insecure.");
		}

		// Authorization settings
		if (this.enableAlwaysTrueAuthorization) {
			AccessRuleDTO fullAuthorizationForEveryone = new AccessRuleDTO();
			fullAuthorizationForEveryone.allow = "*";
			brokerConfiguration.access_rules.add(fullAuthorizationForEveryone);
		}

		VirtualHostDTO host = new VirtualHostDTO();
		// Brokers support multiple virtual hosts.
		// id = default: https://issues.apache.org/jira/browse/APLO-307
		{
			host.id = "default";

			if (this.enableLocalhostAccess) {
				host.host_names.add("localhost");
				host.host_names.add("127.0.0.1");
			}

			if (this.enableExternalAccess) {
				host.host_names.add("0.0.0.0");
			}

			brokerConfiguration.virtual_hosts.add(host);

			// Create some queues by configuration
			host.queues.addAll(this.queues);
		}

		// Add user headers to each protocol if supported
		this.connectors.forEach(connector -> {
			connector.protocols.forEach(protocol -> {

				if (protocol instanceof StompDTO) {
					((StompDTO) protocol).add_user_headers.addAll(this.addUserHeaders);
				} else {
					throw new RuntimeException("Unsupported protocol: " + protocol);
				}

			});

		});

		// Add each protocol
		brokerConfiguration.connectors.addAll(this.connectors);

		// Fires up the web admin console on HTTP.
		if (this.webAdminPort > 0) {
			String webAdminUrl = "http://127.0.0.1:3333";
			log.warn("Enabling Web Admin interface on {}", webAdminUrl);
			WebAdminDTO webadmin = new WebAdminDTO();
			webadmin.bind = webAdminUrl;
			brokerConfiguration.web_admins.add(webadmin);
		}

		return brokerConfiguration;
	}

}