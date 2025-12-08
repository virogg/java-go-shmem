package com.jgshmem.example;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.jgshmem.config.Config;
import com.jgshmem.message.PayloadMessage;
import com.jgshmem.ring.RingConsumer;
import com.jgshmem.ring.RingProducer;
import com.jgshmem.ring.WaitingRingConsumer;
import com.jgshmem.ring.WaitingRingProducer;

public class JavaEchoService {

	public static void main(String[] args) throws Exception {

		Path configPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("..", "shared", "config.json");
		Config cfg = Config.load(configPath);

		final int maxPayloadSize = cfg.go2java.maxObjectSize - 4;

		com.jgshmem.utils.Builder<PayloadMessage> builder = new com.jgshmem.utils.Builder<PayloadMessage>() {
			@Override
			public PayloadMessage newInstance() {
				return new PayloadMessage(maxPayloadSize);
			}
		};

		final RingConsumer<PayloadMessage> consumer =
			new WaitingRingConsumer<>(cfg.go2java.capacity, cfg.go2java.maxObjectSize, builder, cfg.go2java);

		final RingProducer<PayloadMessage> producer =
			new WaitingRingProducer<>(cfg.java2go.capacity, cfg.java2go.maxObjectSize, builder, cfg.java2go);
		
		System.out.println("JavaEchoService up");
		System.out.println("go2java: backend=" + cfg.go2java.backend + " name=" + (cfg.go2java.backend.equals("posix_shm") ? cfg.go2java.shmName : cfg.go2java.filename)
			+ " cap=" + consumer.getCapacity()
			+ " lastFetchedSeq=" + consumer.getLastFetchedSequence());
		System.out.println("java2go: backend=" + cfg.java2go.backend + " name=" + (cfg.java2go.backend.equals("posix_shm") ? cfg.java2go.shmName : cfg.java2go.filename)
			+ " cap=" + producer.getCapacity()
			+ " lastOfferedSeq=" + producer.getLastOfferedSequence());
		
		while (true) {

			long avail = consumer.availableToFetch();

			if (avail <= 0) {
				continue; // busy spin
			}

			for (long i = 0; i < avail; i++) {
				PayloadMessage req = consumer.fetch();

				PayloadMessage resp;
				while ((resp = producer.nextToDispatch()) == null) {
					// busy spin while waiting for ring space
				}

				// Echo back the payload
				// TODO: parse the payload and process it
				resp.payloadSize = req.payloadSize;
				if (req.payloadSize > 0) {
					req.payload.position(0).limit(req.payloadSize);
					resp.payload.clear();
					resp.payload.put(req.payload);
				}
			}

			consumer.doneFetching();
			producer.flush();
		}
	}
}
