package de.farberg.apollo.factory;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.activemq.apollo.broker.Broker;
import org.apache.activemq.apollo.dto.BrokerDTO;
import org.apache.activemq.apollo.dto.XmlCodec;

public class ApolloEmbeddedFactory {
	public static Broker start(BrokerDTO brokerConfiguration) throws Exception {

		// Creating and initially configuring the broker.
		Broker broker = new Broker();
		broker.setTmp(new File("./target/out/tmp"));

		broker.setConfig(brokerConfiguration);

		{
			ByteArrayOutputStream outstream = new ByteArrayOutputStream();
			XmlCodec.encode(brokerConfiguration, outstream, true);
		}

		// No start the broker and wait for completion
		final Object waitLock = new Object();

		broker.start(new Runnable() {

			@Override
			public void run() {
				synchronized (waitLock) {
					waitLock.notifyAll();
				}
			}
		});

		// Wait for the server to have started
		synchronized (waitLock) {
			waitLock.wait(20000);
		}

		return broker;
	}
}
