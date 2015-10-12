package val;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;

public class MyNioProxy {
    private final ByteBuffer buffer = ByteBuffer.allocate(4096);
    final String OK = "HTTP/1.1 200 OK\r\n\r\n";

    private void doWork() throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(8001));
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        while (true) {
            accept(serverSocketChannel, selector);
            if (!process(selector)) {
                // no action, give it some rest
                Thread.sleep(200);
            }
        }
    }

    private boolean process(Selector selector) throws IOException {
        boolean processed = false;
        if (selector.selectNow() > 0) {
            Set<SelectionKey> keySet = selector.selectedKeys();
            for (SelectionKey key : new HashSet<SelectionKey>(keySet)) {
                keySet.remove(key);
                processed = true;
                buffer.clear();

                if (key.isReadable()) {
                    SocketChannel ch = (SocketChannel) key.channel();
                    SocketChannel sink = (SocketChannel) key.attachment();

                    if (sink == null) {
                        sink = initializeSink(selector, key, ch);
                    }

                    boolean shouldClose = copyLarge(ch, sink);

                    if (shouldClose) {
                        System.out.println("Closing " + sink);
                        ch.close();
                        sink.close();
                    }
                } else {
                    throw new IllegalStateException("Key should only be ready for " + key.readyOps());
                }
            }
        }
        return processed;
    }

    private boolean copyLarge(SocketChannel ch, SocketChannel sink) throws IOException {
        int bytesRead;
        while ((bytesRead = ch.read(buffer)) > 0) {
            buffer.flip();
            sink.write(buffer);
            buffer.clear();
        }
        return bytesRead == -1;
    }

    private SocketChannel initializeSink(Selector selector, SelectionKey key, SocketChannel ch) throws IOException {
        int bytesRead = ch.read(buffer);
        buffer.flip();
        String request = parseHttpRequest(buffer);
        System.out.println("Request: \n" + request);
        SocketChannel sink = createChannelBasedOnHttpRequest(request);
        key.attach(sink);
        sink.register(selector, SelectionKey.OP_READ, ch);

        if (request.toUpperCase().startsWith("GET")) {
            buffer.rewind();
            sink.write(buffer);
        } else if(request.toUpperCase().startsWith("CONNECT")) {
            buffer.clear();
            buffer.put(OK.getBytes());
            buffer.flip();
            ch.write(buffer);
        } else {
            throw new IllegalStateException("Unknown request:" + request);
        }
        buffer.clear();
        return sink;
    }

    private void accept(ServerSocketChannel serverSocketChannel, Selector selector) throws IOException {
        SocketChannel channel = serverSocketChannel.accept();
        if (channel != null) {
            System.out.println("Accepted " + channel);
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
        }
    }

    private SocketChannel createChannelBasedOnHttpRequest(String httpRequest) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(httpRequest));
        String host = null;
        while (reader.ready()) {
            String line = reader.readLine();
            if (line.toLowerCase().startsWith("host:")) {
                host = line.substring("host:".length()).trim();
                break;
            }
        }

        int port = 80;
        String[] hostPort = host.split(":");
        if (hostPort.length > 1) {
            port = Integer.parseInt(hostPort[1]);
        }

        SocketChannel channel = SocketChannel.open(new InetSocketAddress(hostPort[0], port));
        channel.configureBlocking(false);
        return channel;
    }

    private String parseHttpRequest(ByteBuffer buffer) {
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        return sb.toString();
    }

    public static void main(String[] asdf) throws Exception {
        new MyNioProxy().doWork();
    }
}
