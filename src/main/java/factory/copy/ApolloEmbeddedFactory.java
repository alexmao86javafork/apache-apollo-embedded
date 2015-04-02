package factory.copy;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.activemq.apollo.broker.Broker;
import org.apache.activemq.apollo.dto.BrokerDTO;
import org.apache.activemq.apollo.dto.XmlCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApolloEmbeddedFactory {
	private static final Logger log = LoggerFactory.getLogger(ApolloEmbeddedFactory.class);

	public static Broker start(BrokerDTO brokerConfiguration) throws Exception {

		log.info("Starting embedded message queuing broker...");

		// Creating and initially configuring the broker.
		Broker broker = new Broker();
		broker.setTmp(new File("./target/out/tmp"));

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
