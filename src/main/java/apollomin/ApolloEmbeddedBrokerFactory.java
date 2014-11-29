package apollomin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.activemq.apollo.broker.Broker;
import org.apache.activemq.apollo.dto.AcceptingConnectorDTO;
import org.apache.activemq.apollo.dto.AccessRuleDTO;
import org.apache.activemq.apollo.dto.AddUserHeaderDTO;
import org.apache.activemq.apollo.dto.AuthenticationDTO;
import org.apache.activemq.apollo.dto.BrokerDTO;
import org.apache.activemq.apollo.dto.QueueDTO;
import org.apache.activemq.apollo.dto.VirtualHostDTO;
import org.apache.activemq.apollo.dto.XmlCodec;
import org.apache.activemq.apollo.stomp.dto.StompDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApolloEmbeddedBrokerFactory {
	private static final Logger log = LoggerFactory.getLogger(ApolloEmbeddedBrokerFactory.class);
	public static final String AUTHENTICATED_USER_ID_HEADER_NAME = "authenticated-user-id";

	public static Broker create(int webSocketPort, int stompTcpPort, Set<String> queueNames, int maxConnectionLimit, String jaasConfigFile)
			throws Exception {

		log.info("Starting embedded message queuing broker...");

		// Creating and initially configuring the broker.
		Broker broker = new Broker();
		broker.setTmp(new File("./target/out/tmp"));

		// Create the configuration
		BrokerDTO brokerConfiguration = new BrokerDTO();
		{
			// Authentication settings
			if (jaasConfigFile != null) {
				log.info("Enabling security on the broker using JAAS file {}", jaasConfigFile);

				// Set the location of the JAAS configuration file accordingly
				System.setProperty("java.security.auth.login.config", jaasConfigFile);

				// Enable authentication
				brokerConfiguration.authentication = new AuthenticationDTO();
				brokerConfiguration.authentication.enabled = true;
				brokerConfiguration.authentication.domain = "Internal";

				// Add the name of the recognized principal class to support htpasswd-based authentication. See
				// http://activemq.apache.org/apollo/documentation/user-manual.html#Authentication for details
				// Collection<String> principalClasses = Arrays.asList(UserPrincipal.class.getCanonicalName());
				// brokerConfiguration.authentication.user_principal_kinds().addAll(principalClasses);
				// brokerConfiguration.authentication.acl_principal_kinds().addAll(principalClasses);
			} else {
				log.warn("Not using authentication/autorization. This configuration is insecure.");
			}

			// Authorization settings
			{
				AccessRuleDTO fullAuthorizationForEveryone = new AccessRuleDTO();
				fullAuthorizationForEveryone.allow = "*";
				brokerConfiguration.access_rules.add(fullAuthorizationForEveryone);
			}

			// Brokers support multiple virtual hosts.
			// id = default: https://issues.apache.org/jira/browse/APLO-307
			VirtualHostDTO host = new VirtualHostDTO();
			host.id = "default";
			host.host_names.add("0.0.0.0");
			host.host_names.add("localhost");
			host.host_names.add("127.0.0.1");
			brokerConfiguration.virtual_hosts.add(host);

			// Create some queues by configuration
			{
				for (String name : queueNames) {
					QueueDTO queue = new QueueDTO();
					queue.persistent = false;
					queue.auto_delete_after = 0;
					queue.id = name;
					host.queues.add(queue);
				}
			}

			// This adds support for message sender identification (cf.
			// http://activemq.apache.org/apollo/documentation/stomp-manual.html)
			// This DTO is added to the individual transport configurations
			// TODO Maybe add <add_user_header
			// kind="org.apache.activemq.apollo.broker.security.SourceAddressPrincipal">sender-ip</add_user_header>

			List<AddUserHeaderDTO> addUserHeaders = new ArrayList<>();
			{
				AddUserHeaderDTO authenticatedUserHeader = new AddUserHeaderDTO();
				addUserHeaders.add(authenticatedUserHeader);

				// TODO This does not work
				authenticatedUserHeader.kind = "org.apache.activemq.jaas.UserPrincipal";
				authenticatedUserHeader.name = AUTHENTICATED_USER_ID_HEADER_NAME;
				authenticatedUserHeader.kind = "*";
				authenticatedUserHeader.separator = ",";
			}
			{
				AddUserHeaderDTO senderIpHeader = new AddUserHeaderDTO();
				addUserHeaders.add(senderIpHeader);

				senderIpHeader.kind = "org.apache.activemq.apollo.broker.security.SourceAddressPrincipal";
				senderIpHeader.name = "sender-ip";
				senderIpHeader.separator = ",";
			}

			// Control which ports and protocols the broker binds and accepts
			{
				AcceptingConnectorDTO connector = new AcceptingConnectorDTO();
				connector.id = "tcp";
				connector.bind = "tcp://0.0.0.0:" + stompTcpPort;
				connector.connection_limit = maxConnectionLimit;

				StompDTO stompDto = new StompDTO();
				connector.other.add(stompDto);
				// TODO Verify that this really works once authentication is used
				stompDto.add_user_headers.addAll(addUserHeaders);

				brokerConfiguration.connectors.add(connector);
			}

		}

		broker.setConfig(brokerConfiguration);

		{
			ByteArrayOutputStream outstream = new ByteArrayOutputStream();
			XmlCodec.encode(brokerConfiguration, outstream, true);
			log.debug("Using broker configuration: \n" + outstream.toString());
		}

		// No start the broker and wait for completion
		log.debug("Waiting for server to start...");

		final Object waitLock = new Object();

		broker.start(new Runnable() {

			@Override
			public void run() {
				synchronized (waitLock) {
					waitLock.notifyAll();
				}
				log.debug("Server ready...");
			}
		});

		// Wait for the server to have started
		synchronized (waitLock) {
			waitLock.wait(20000);
		}

		return broker;
	}
}
