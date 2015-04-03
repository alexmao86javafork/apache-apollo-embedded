package de.farberg.apollo.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.activemq.apollo.amqp.dto.AmqpDTO;
import org.apache.activemq.apollo.dto.AcceptingConnectorDTO;
import org.apache.activemq.apollo.dto.AccessRuleDTO;
import org.apache.activemq.apollo.dto.AddUserHeaderDTO;
import org.apache.activemq.apollo.dto.AuthenticationDTO;
import org.apache.activemq.apollo.dto.BrokerDTO;
import org.apache.activemq.apollo.dto.DetectDTO;
import org.apache.activemq.apollo.dto.QueueDTO;
import org.apache.activemq.apollo.dto.VirtualHostDTO;
import org.apache.activemq.apollo.dto.WebAdminDTO;
import org.apache.activemq.apollo.mqtt.dto.MqttDTO;
import org.apache.activemq.apollo.openwire.dto.OpenwireDTO;
import org.apache.activemq.apollo.stomp.dto.StompDTO;
import org.apache.activemq.jaas.UserPrincipal;

public class ApolloConfigurationBuilder {
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

	private AcceptingConnectorDTO defaultAcceptingConnectorDTO(String id, String protocolPrefix, String detectProtocol, int port) {
		AcceptingConnectorDTO connector = new AcceptingConnectorDTO();
		connector.id = id;
		connector.bind = protocolPrefix + "://0.0.0.0:" + port;
		connector.connection_limit = defaultMaxConnectionLimit;

		DetectDTO detectDTO = new DetectDTO();
		detectDTO.protocols = detectProtocol;

		connector.other.add(detectDTO);

		return connector;
	}

	public ApolloConfigurationBuilder stomp(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("stomp@" + port, "tcp", "stomp", port);

		StompDTO stompDto = new StompDTO();
		connector.protocols.add(stompDto);

		this.connectors.add(connector);
		return this;
	}

	public ApolloConfigurationBuilder stompOverWebsocket(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("websockets-stomp@" + port, "ws", "stomp", port);

		StompDTO stompDto = new StompDTO();
		connector.protocols.add(stompDto);

		this.connectors.add(connector);
		return this;
	}

	public ApolloConfigurationBuilder amqp(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("amqp@" + port, "tcp", "amqp", port);

		AmqpDTO amqpDto = new AmqpDTO();

		connector.protocols.add(amqpDto);
		this.connectors.add(connector);

		return this;
	}

	public ApolloConfigurationBuilder mqtt(int port) {

		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("mqtt@" + port, "tcp", "mqtt", port);

		MqttDTO mqttDto = new MqttDTO();

		connector.protocols.add(mqttDto);
		this.connectors.add(connector);

		return this;
	}

	public ApolloConfigurationBuilder openWire(int port) {
		AcceptingConnectorDTO connector = defaultAcceptingConnectorDTO("openwire@" + port, "tcp", "openwire", port);

		OpenwireDTO openWireDto = new OpenwireDTO();
		connector.protocols.add(openWireDto);

		this.connectors.add(connector);
		return this;
	}

	public ApolloConfigurationBuilder defaultMaxConnectionLimit(int limit) {
		this.defaultMaxConnectionLimit = 5000;
		return this;
	}

	public BrokerDTO build() {
		// Create the configuration
		BrokerDTO brokerConfiguration = new BrokerDTO();

		// Authentication settings
		if (jaasConfigFile != null) {
			// Set the location of the JAAS configuration file accordingly
			System.setProperty("java.security.auth.login.config", jaasConfigFile);

			// Enable authentication
			brokerConfiguration.authentication = new AuthenticationDTO();
			brokerConfiguration.authentication.enabled = true;
			brokerConfiguration.authentication.domain = "Internal";

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

				} else if (protocol instanceof OpenwireDTO) {
					((OpenwireDTO) protocol).add_jmsxuserid = true;

				} else if (protocol instanceof AmqpDTO) {
					// No support for authenticated user headers

				} else if (protocol instanceof MqttDTO) {
					// No support for authenticated user headers

				} else {
					throw new RuntimeException("Unsupported protocol: " + protocol);
				}

			});

		});

		// Add each protocol
		brokerConfiguration.connectors.addAll(this.connectors);

		// Fires up the web admin console on HTTP.
		if (this.webAdminPort > 0) {
			String webAdminUrl = "http://127.0.0.1:" + this.webAdminPort;
			WebAdminDTO webadmin = new WebAdminDTO();
			webadmin.bind = webAdminUrl;
			brokerConfiguration.web_admins.add(webadmin);
		}

		return brokerConfiguration;
	}
}