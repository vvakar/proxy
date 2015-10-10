package val;

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

/**
 * Created by valentin.vakar on 10/9/15.
 */
public class MyNioProxy {
    private void doWork() throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(8001));
        serverSocketChannel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        Selector selector = Selector.open();
        while (true) {
            SocketChannel channel = serverSocketChannel.accept();
            if (channel != null) {
                System.out.println("Accepted");
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_READ);
            }


            int selected;
            if ((selected = selector.selectNow()) > 0) {
                Set<SelectionKey> keySet = selector.selectedKeys();
                for (SelectionKey key : new HashSet<SelectionKey>(keySet)) {
                    keySet.remove(key);
                    if (key.isReadable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        SocketChannel sink = (SocketChannel) key.attachment();


                        int bytesRead;
                        if (sink == null) {
                            bytesRead = ch.read(buffer);
                            buffer.flip();
                            String request = parseHttpRequest(buffer);
                            System.out.println("Request: \n" + request);
                            sink = createChannelBasedOnHttpRequest(request);
                            key.attach(sink);
                            sink.register(selector, SelectionKey.OP_READ, channel);
                            buffer.rewind();
                            sink.write(buffer);
                            buffer.clear();
                        }

                        while ((bytesRead = ch.read(buffer)) > 0) {
                            buffer.flip();
                            sink.write(buffer);
                            buffer.clear();
                        }

                        if (bytesRead == -1) {
                            System.out.println("Closing " + sink);
                            ch.close();
                            sink.close();
                        }

                        buffer.clear();
                    } else {
                        throw new IllegalStateException("Key should not be ready for " + key.readyOps());
                    }
                }
            } else {
                Thread.sleep(300);
            }
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

    private int readBuffer(ByteBuffer buffer, SocketChannel ch) throws IOException, InterruptedException {
        int bytesRead;
        while ((bytesRead = ch.read(buffer)) > 0) {

            buffer.flip();

            while (buffer.hasRemaining()) {
                System.out.print((char) buffer.get());
            }
            buffer.clear();
        }
        return bytesRead;
    }

    public static void main(String[] asdf) throws Exception {
        new MyNioProxy().doWork();
    }
}
