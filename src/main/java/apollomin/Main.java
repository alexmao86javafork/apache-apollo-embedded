package apollomin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.activemq.apollo.broker.Broker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import asia.stampy.client.message.send.SendMessage;
import asia.stampy.client.message.subscribe.SubscribeMessage;
import asia.stampy.client.netty.ClientNettyMessageGateway;
import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.server.message.message.MessageMessage;
import de.uniluebeck.itm.util.logging.Logging;

public class Main {

	public static void main(String[] args) throws Exception {
		// Set up logging
		SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)
		SLF4JBridgeHandler.install();
		Logging.setLoggingDefaults();

		final Logger log = LoggerFactory.getLogger(Main.class);

		// Create an embedded broker instance
		Set<String> queueNames = new HashSet<>(Arrays.asList("queue1"));
		Broker broker = ApolloEmbeddedBrokerFactory.create(8888, 8889, queueNames, 100, "src/main/resources/demo.jaas");

		// Create a listener
		ClientNettyMessageGateway listener = StompClientStampyFactory.createClientConnection("localhost", 8889, "demo", "demo");
		listener.broadcastMessage(new SubscribeMessage("/queue/queue1", "sub-0"));

		listener.addMessageListener(new StampyMessageListener() {

			@Override
			public void messageReceived(StampyMessage<?> genericMessage, HostPort hostPort) throws Exception {
				MessageMessage message = (MessageMessage) genericMessage;

				log.info("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
				for (Entry<String, String> entry : message.getHeader().getHeaders().entrySet()) {
					log.info("Message header: key({}) = value({})", entry.getKey(), entry.getValue());
				}
				log.info("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
			}

			@Override
			public boolean isForMessage(StampyMessage<?> message) {
				return true;
			}

			@Override
			public StompMessageType[] getMessageTypes() {
				return new StompMessageType[] { StompMessageType.MESSAGE };
			}
		});

		// Create a local producer
		ClientNettyMessageGateway producer = StompClientStampyFactory.createClientConnection("localhost", 8889, "demo", "demo");

		SendMessage message = new SendMessage();
		message.getHeader().setDestination("/queue/queue1");
		message.setBody("hello world");
		message.getHeader().addHeader("ttl", "5000");
		producer.sendMessage(message, producer.getConnectedHostPorts().iterator().next());

		// Shutdown
		Thread.sleep(2000);
		listener.shutdown();
		producer.shutdown();
		broker.stop(new Runnable() {

			@Override
			public void run() {
				System.exit(0);
			}
		});
	}
}
