package org.jenkinsci.demo.cli;

import hudson.cli.CLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Future;
import hudson.remoting.Pipe;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws Exception {
        // establish a connection to the Jenkins server
        CLI cli = new CLI(new URL("http://localhost:8080/"));
        try {
            // this is the private key that authenticates ourselves to the server
            KeyPair key = cli.loadKey(new File("./id_rsa"));

            // perform authentication, and in the end obtain the public key that identifies the server
            // (the equivalent of SSH host key.) In this demo, I'm not verifying that we are talking who
            // we are supposed to be talking to, but you can do so by comparing the public key to the record.
            PublicKey server = cli.authenticate(Collections.singleton(key));
            System.out.println("Server key is "+server);

            // by default, CLI connections are restricted capability-wise, to protect servers from clients.
            // But now we want to start using the channel directly with its full capability, so we try
            // to upgrade the connection. This requires the administer access to the system.
            cli.upgrade();

            // with that, we can now directly use Channel and do all the operations that it can do.
            Channel channel = cli.getChannel();

            // execute a closure on the server, send the return value (or exception) back.
            // note that Jenkins server doesn't have this code on its JVM, but the remoting layer is transparently
            // sending that for you.
            int r = channel.call(new Callable<Integer, IOException>() {
                public Integer call() throws IOException {
                    // this portion executes inside the Jenkins server JVM.
                    return Jenkins.getInstance().getItems().size();
                }
            });
            System.out.println("The server has "+r+" jobs");

            // Showing how you do asynchronous execution. You can have the caller unblock immediately
            // and wait for the completion later.
            //
            // Pipe is a mechanism for creating a connected InputStream/OutputStream pair between two ends
            // of channel. In this demo, I'm sending "hello world" from client to the server.
            final Pipe pipe = Pipe.createLocalToRemote();
            Future<Void> f = channel.callAsync(new Callable<Void, IOException>() {
                public Void call() throws IOException {
                    // this portion executes inside the Jenkins server JVM.
                    IOUtils.copy(pipe.getIn(), System.out);
                    return null;
                }
            });
            IOUtils.copy(new ByteArrayInputStream("Hello, world!\n".getBytes()), pipe.getOut());
            pipe.getOut().close();
            f.get();

            // see the javadoc of the remoting module for all the other things you can do.
        } finally {
            cli.close();
        }
    }
}
