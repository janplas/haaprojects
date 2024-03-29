package c4j.haa.ems.sample.tibjms;
/* 
 * Copyright 2001-2006 TIBCO Software Inc.
 * All rights reserved.
 * For more information, please contact:
 * TIBCO Software Inc., Palo Alto, California, USA
 * 
 * $Id: tibjmsMsgConsumerPerf.java 22715 2006-08-10 21:06:15Z $
 * 
 */

/*
 * This is a sample message consumer class used to measure performance.
 * 
 * For the the specified number of threads this sample creates a 
 * session and a message consumer for the specified destination.
 * Once the specified number of messages are consumed the performance
 * results are printed and the program exits.
 *
 * Usage:  java tibjmsMsgConsumerPerf  [options]
 *
 *  where options are:
 *
 *   -server       <url>         EMS server URL. Default is
 *                               "tcp://localhost:7222".
 *   -user         <username>    User name. Default is null.
 *   -password     <password>    User password. Default is null.
 *   -topic        <topic-name>  Topic name. Default is "topic.sample".
 *   -queue        <queue-name>  Queue name. No default.
 *   -count        <num msgs>    Number of messages to consume. Default is 10k.
 *   -time         <seconds>     Number of seconds to run. Default is 0 (forever).
 *   -threads      <num threads> Number of message consumer threads. Default is 1.
 *   -connections  <num conns>   Number of message consumer connections. Default is 1.
 *   -txnsize      <num msgs>    Number of messages per consumer transaction. Default is 0.
 *   -durable      <name>        Durable subscription name.
 *   -selector     <selector>    Message selector for consumer threads. No default.
 *   -ackmode      <mode>        Message acknowledge mode. Default is AUTO.
 *                               Other values: DUPS_OK, CLIENT EXPLICIT_CLIENT,
 *                               EXPLICIT_CLIENT_DUPS_OK and NO.
 *   -factory      <lookup name> Lookup name for connection factory.
 *   -uniquedests                Each consumer thread uses a unique destination.
 */

import java.util.*;
import javax.jms.*;
import javax.naming.*;

public class tibjmsMsgConsumerPerf implements Runnable
{
    // parameters
    private String serverUrl = "tcp://localhost:7222";
    private String username = null;
    private String password = null;
    private String destName = "topic.sample";
    private String durableName = null;
    private String selector = null;
    private String factoryName = null;
    private boolean uniqueDests = false;
    private boolean useTopic = true;
    private int txnSize = 0;
    private int count = 10000;
    private int runTime = 0;
    private int threads = 1;
    private int connections = 1;
    private int ackMode = Session.AUTO_ACKNOWLEDGE;

    // variables
    private int connIter;
    private int destIter;
    private int nameIter;
    private int recvCount;
    private long startTime;
    private long endTime;
    private long elapsed;
    private boolean stopNow;
    private Vector connsVector;

    /**
     * Constructor
     * 
     * @param args the command line arguments
     */
    public tibjmsMsgConsumerPerf(String[] args)
    {
        parseArgs(args);

        try {
            tibjmsUtilities.initSSLParams(serverUrl,args);

            // print parameters
            System.err.println();
            System.err.println("------------------------------------------------------------------------");
            System.err.println("tibjmsMsgConsumerPerf SAMPLE");
            System.err.println("------------------------------------------------------------------------");
            System.err.println("Server....................... " + serverUrl);
            System.err.println("User......................... " + username);
            System.err.println("Destination.................. " + destName);
            System.err.println("Consumer Threads............. " + threads);
            System.err.println("Consumer Connections......... " + connections);
            System.err.println("Ack Mode..................... " + ackModeName(ackMode));
            System.err.println("Durable...................... " + (durableName != null));
            System.err.println("Selector..................... " + selector);
            if (txnSize > 0)
                System.err.println("Transaction Size............. " + txnSize);
            if (factoryName != null)
                System.err.println("Connection Factory........... " + factoryName);
            System.err.println("------------------------------------------------------------------------");
            System.err.println();

            // lookup the connection factory
            ConnectionFactory factory = null;
            if (factoryName != null)
            {
                tibjmsUtilities.initJNDI(serverUrl);
                factory = (ConnectionFactory) tibjmsUtilities.lookup(factoryName);
            }
            else
            {
                factory = new com.tibco.tibjms.TibjmsConnectionFactory(serverUrl);
            }
            
            // create the connections
            connsVector = new Vector(connections);
            for (int i=0;i<connections;i++)
            {
                Connection conn = factory.createConnection(username, password);
                conn.start();
                connsVector.add(conn);
            }

            // create the consumer threads
            Vector tv = new Vector(threads);
            for (int i=0;i<threads;i++)
            {
                Thread t = new Thread(this);
                tv.add(t);
                t.start();
            }

            // run for the specified amout of time
            if (runTime > 0)
            {
                try 
                {
                    Thread.sleep(runTime * 1000);
                } 
                catch (InterruptedException e) {}

                // ensure consumer threads stop now
                stopNow = true;
                for (int i=0;i<threads;i++)
                {
                    Thread t = (Thread)tv.elementAt(i);
                    t.interrupt();
                }
            }

            // wait for the consumer threads to exit
            for (int i=0;i<threads;i++)
            {
                Thread t = (Thread)tv.elementAt(i);
                try 
                {
                    t.join();
                } 
                catch (InterruptedException e) {}
            }

            // close the connections
            for (int i=0;i<connections;i++) 
            {
                Connection conn = (Connection)connsVector.elementAt(i);
                conn.close();
            }

            // print performance
            System.err.println(getPerformance());
        }
        catch (NamingException e)
        {
            e.printStackTrace();
        }
        catch (JMSException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Returns a connection.
     */
    private synchronized Connection getConnection()
    {
        Connection connection = (Connection)connsVector.elementAt(connIter++);
        if (connIter == connections)
            connIter = 0;
        return connection;
    }

    /**
     * Returns a destination.
     */
    private synchronized Destination getDestination(Session s) throws JMSException
    {
        if (useTopic)
        {
            if (!uniqueDests)
                return s.createTopic(destName);
            else
                return s.createTopic(destName + "." + ++destIter);
        }
        else
        {
            if (!uniqueDests)
                return s.createQueue(destName);
            else
                return s.createQueue(destName + "." + ++destIter);
        }
    }

    /**
     * Returns a unique subscription name if durable
     * subscriptions are specified.
     */
    private synchronized String getSubscriptionName()
    {
        if (durableName != null)
            return durableName + ++nameIter;
        else
            return null;
    }

    /**
     * Update the total receive count.
     */
    private synchronized void countReceives(int count)
    {
        recvCount += count;
    }

    /**
     * The consumer thread's run method.
     */
    public void run()
    {
        Session session = null;
        MessageConsumer msgConsumer = null;
        String subscriptionName = getSubscriptionName();
        int msgCount = 0;
        
        try 
        {
            Thread.sleep(250);
        } catch (InterruptedException e) {}

        try {
            // get the connection
            Connection connection = getConnection();
            
            // create a session
            session = connection.createSession(txnSize > 0, ackMode);
            
            // get the destination
            Destination destination = getDestination(session);
            
            // create the consumer
            if (subscriptionName == null)
                msgConsumer = session.createConsumer(destination, selector);
            else
                msgConsumer = session.createDurableSubscriber((Topic)destination,
                                                              subscriptionName,
                                                              selector,
                                                              false);

            // receive messages
            while ((count == 0 || msgCount < (count/threads)) && !stopNow)
            {
                Message msg = msgConsumer.receive();
                if (msg == null)
                    break;

                if (msgCount == 0)
                    startTiming();

                msgCount++;
                
                // acknowledge the message if necessary
                if (ackMode == Session.CLIENT_ACKNOWLEDGE ||
                    ackMode == com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE ||
                    ackMode == com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE)
                    msg.acknowledge();

                // commit the transaction if necessary
                if (txnSize > 0 && msgCount % txnSize == 0)
                    session.commit();
                
                // force the uncompression of compressed messages
                if (msg.getBooleanProperty("JMS_TIBCO_COMPRESS"))
                    ((BytesMessage) msg).getBodyLength();
            }
        }
        catch (JMSException e)
        {
            if (!stopNow)
                e.printStackTrace();
        }

        // commit any remaining messages
        if (txnSize > 0)
        {
            try 
            {
                session.commit();
            }
            catch (JMSException e) 
            {
                if (!stopNow)
                    e.printStackTrace();
            }
        }
        
        stopTiming();

        countReceives(msgCount);
        
        // unsubscribe durable subscription
        if (subscriptionName != null) 
        {
            try 
            {
                if (msgConsumer != null)
                    msgConsumer.close();
                if (session != null)
                    session.unsubscribe(subscriptionName);
            }
            catch (JMSException e) 
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Convert acknowledge mode to a string.
     */
    private static String ackModeName(int ackMode) {
        switch(ackMode)
        {
            case Session.DUPS_OK_ACKNOWLEDGE:      
                return "DUPS_OK_ACKNOWLEDGE";
            case Session.AUTO_ACKNOWLEDGE:         
                return "AUTO_ACKNOWLEDGE";
            case Session.CLIENT_ACKNOWLEDGE:       
                return "CLIENT_ACKNOWLEDGE";
            case com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE:         
                return "EXPLICIT_CLIENT_ACKNOWLEDGE";
            case com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE:         
                return "EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE";
            case com.tibco.tibjms.Tibjms.NO_ACKNOWLEDGE:     
                return "NO_ACKNOWLEDGE";
            default:                                         
                return "(unknown)";
        }
    }

    private synchronized void startTiming()
    {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
    }
    
    private synchronized void stopTiming()
    {
        endTime = System.currentTimeMillis();
    }
    
    /**
     * Get the performance results.
     */
    private String getPerformance() 
    {
        if (endTime > startTime)
        {
            elapsed = endTime - startTime;
            double seconds = elapsed/1000.0;
            int perf = (int)((recvCount * 1000.0)/elapsed);
            return (recvCount + " times took " + seconds + " seconds, performance is " + perf + " messages/second");
        }
        else
        {
            return "interval too short to calculate a message rate";
        }
    }

    /**
     * Print usage and exit.
     */
    private void usage()
    {
        System.err.println();
        System.err.println("Usage: java tibjmsMsgConsumerPerf [options] [ssl options]");
        System.err.println();
        System.err.println("  where options are:");
        System.err.println();
        System.err.println("    -server       <url>         - Server URL. Default is \"tcp://localhost:7222\".");
        System.err.println("    -user         <username>    - User name. Default is null.");
        System.err.println("    -password     <password>    - User password. Default is null.");
        System.err.println("    -topic        <topic-name>  - Topic name. Default is \"topic.sample\".");
        System.err.println("    -queue        <queue-name>  - Queue name. No default.");
        System.err.println("    -count        <num msgs>    - Number of messages to consume. Default is 10k.");
        System.err.println("    -time         <seconds>     - Number of seconds to run. Default is 0.");
        System.err.println("    -threads      <num threads> - Number of consumer threads. Default is 1.");
        System.err.println("    -connections  <num conns>   - Number of consumer connections. Default is 1.");
        System.err.println("    -txnsize      <num msgs>    - Number of messages per consumer transaction.");
        System.err.println("    -durable      <name>        - Durable subscription name. No default.");
        System.err.println("    -selector     <selector>    - Message selector for consumers. No default.");
        System.err.println("    -ackmode      <mode>        - Message acknowledge mode. Default is AUTO.");
        System.err.println("                                  Other values: DUPS_OK, CLIENT EXPLICIT_CLIENT,");
        System.err.println("                                  EXPLICIT_CLIENT_DUPS_OK and NO.");
        System.err.println("    -factory      <lookup name> - Lookup name for connection factory.");
        System.err.println("    -uniquedests                - Each consumer thread uses a unique destination.");
        System.err.println("    -help-ssl                   - Print help on SSL parameters.");
        System.exit(0);
    }

    /**
     * Parse the command line arguments.
     */
    private void parseArgs(String[] args)
    {
        int i=0;

        while(i < args.length)
        {
            if (args[i].compareTo("-server")==0)
            {
                if ((i+1) >= args.length) usage();
                serverUrl = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-topic")==0)
            {
                if ((i+1) >= args.length) usage();
                destName = args[i+1];
                useTopic = true;
                i += 2;
            }
            else if (args[i].compareTo("-queue")==0)
            {
                if ((i+1) >= args.length) usage();
                destName = args[i+1];
                useTopic = false;
                i += 2;
            }
            else if (args[i].compareTo("-durable")==0)
            {
                if ((i+1) >= args.length) usage();
                durableName = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-user")==0)
            {
                if ((i+1) >= args.length) usage();
                username = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-help-ssl")==0)
            {
                tibjmsUtilities.sslUsage();
            }
            else if (args[i].compareTo("-password")==0)
            {
                if ((i+1) >= args.length) usage();
                password = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-uniquedests")==0)
            {
                uniqueDests = true;
                i += 1;
            }
            else if (args[i].compareTo("-threads")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    threads = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -threads parameter");
                    usage();
                }
                if (threads < 1) {
                    System.err.println("Error: invalid value of -threads parameter, must be >= 1");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-connections")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    connections = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -connections parameter");
                    usage();
                }
                if (connections < 1) 
                {
                    System.err.println("Error: invalid value of -connections parameter, must be >= 1");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-count")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    count = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -count parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-time")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    runTime = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -time parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-ackmode")==0)
            {
                if ((i+1) >= args.length) usage();
                String dm = args[i+1];
                i += 2;
                if (dm.compareTo("DUPS_OK")==0)
                    ackMode = javax.jms.Session.DUPS_OK_ACKNOWLEDGE;
                else if (dm.compareTo("AUTO")==0)
                    ackMode = javax.jms.Session.AUTO_ACKNOWLEDGE;
                else if (dm.compareTo("CLIENT")==0)
                    ackMode = javax.jms.Session.CLIENT_ACKNOWLEDGE;
                else if (dm.compareTo("EXPLICIT_CLIENT")==0)
                    ackMode = com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_ACKNOWLEDGE;
                else if (dm.compareTo("EXPLICIT_CLIENT_DUPS_OK")==0)
                    ackMode = com.tibco.tibjms.Tibjms.EXPLICIT_CLIENT_DUPS_OK_ACKNOWLEDGE;
                else if (dm.compareTo("NO")==0)
                    ackMode = com.tibco.tibjms.Tibjms.NO_ACKNOWLEDGE;
                else {
                    System.err.println("Error: invalid value of -ackMode parameter");
                    usage();
                }
            }
            else if (args[i].compareTo("-txnsize")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    txnSize = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -txnsize parameter");
                    usage();
                }
                if (txnSize < 1) 
                {
                    System.err.println("Error: invalid value of -txnsize parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-selector")==0)
            {
                if ((i+1) >= args.length) usage();
                selector = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-factory")==0)
            {
                if ((i+1) >= args.length) usage();
                factoryName = args[i+1];
                i += 2;
            }
            else if(args[i].startsWith("-ssl"))
            {
                i += 2;
            }
            else 
            {
                System.err.println("Error: invalid option: " + args[i]);
                usage();
            }
        }

        if (durableName != null && !useTopic)
        {
            System.err.println("Error: -durable cannot be used with -topic");
            usage();
        }
    }

    /**
     * Get the total elapsed time.
     */
    public long getElapsedTime()
    {
        return elapsed;
    }

    /**
     * Get the total consumed message count.
     */
    public int getReceiveCount()
    {
        return recvCount;
    }

    /**
     * main
     */
    public static void main(String[] args)
    {
        tibjmsMsgConsumerPerf t = new tibjmsMsgConsumerPerf(args);
    }
}
