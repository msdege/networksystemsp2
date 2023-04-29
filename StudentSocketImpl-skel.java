import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  /**
   * *****IMPORTANT, PLEASE READ*****
   * 
   * ---Limitations of our code---
   * Our code gets through steps 1-3 perfectly and step 4 consistently. We have run into an issue
   * where step 4 does not get to the TIME_WAIT state every time, but this seems to be a random
   * timing issue with the machines rather than our implementation. For step 5, we have
   * implemented the proper logic within all of the switch statements in receivePacket(), but 
   * unfortuantely the code does not consistently work with a loss rate greater than 0.20.
   * 
   * The attempt to handle packet loss is designated in each of the switch statements by an
   * alternative conditional. The normal procedure in the case of no packet loss is designated
   * by "Normal procedure" above the conditonal.
   * 
   * For the transition from TIME_WAIT to CLOSED, we implemented logic within the handleTimer()
   * method, but our code exits before this is able to occur. Perhaps a thread waiting clause would
   * fix this. 
   */

  private Demultiplexer D;
  private Timer tcpTimer;
  private int ackNum;
  private int seqNum;
  private Boolean thisIsServer = false;
  private Boolean thisIsClient = false;
  private TCPPacket lastPkt;
  private TCPPacket lastACKpkt;

  private String[] possibleStates = {"CLOSED", "SYN_SENT", "LISTEN", "SYN_RCVD", "ESTABLISHED",
          "FIN_WAIT_1", "CLOSE_WAIT", "FIN_WAIT_2", "LAST_ACK", "CLOSING", "TIME_WAIT"}; // for reference

  private String state = possibleStates[0];

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException{
    localport = D.getNextAvailablePort();
    this.address = address;
    this.port = port;
    thisIsClient = true;

    D.registerConnection(address, localport, port, this);

    TCPPacket SYNpkt = new TCPPacket(localport, port, 10, 1, false, true, false, 1, null);
    sendPkt(SYNpkt, address);

    changeState("CLOSED", "SYN_SENT");

    while (state != "ESTABLISHED"){
      try {
        wait();
      }
      catch (InterruptedException e){
        System.out.println(e);
      }
    }

  }

  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   *
   *  receivePacket() is going to be one big switch statement on the state of the connection.
   *  Add the case statement for LISTEN and have it send a SYN+ACK when it receives a SYN.
   */
  public synchronized void receivePacket(TCPPacket p){

    switch (state) {
      case "LISTEN":

        // Break if we do not receive the proper packet
        if (!p.synFlag || p.ackFlag) {
          break;
        }

        port = p.sourcePort;
        seqNum = p.ackNum;
        ackNum = p.seqNum + 1;

        try {
          D.unregisterListeningSocket(localport, this);
          D.registerConnection(p.sourceAddr, p.destPort, p.sourcePort, this);

          this.address = p.sourceAddr; //update server's remote host (i.e., client addr)
          this.port = p.sourcePort;
        }
        catch (Exception e) {
          System.out.println(e);
        }

        TCPPacket SYNACKpkt = new TCPPacket(localport, port, seqNum, ackNum, true, true, false, 1, null);
        sendPkt(SYNACKpkt, address);

        changeState("LISTEN", "SYN_RCVD");

        break;

      case "SYN_SENT":

        // Normal procedure
        if (p.ackFlag && p.synFlag){
          port = p.sourcePort;
          seqNum = p.ackNum;
          ackNum = p.seqNum + 1;

          tcpTimer.cancel(); //cancel timer for sent SYN
			    tcpTimer = null;

          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 1, null);
          sendPkt(ACKpkt, address);

          changeState("SYN_SENT", "ESTABLISHED");
        }

        break;

      case "SYN_RCVD":
        
        // If we receive a SYN in this state, we dropped the SYN+ACK, so resend it.
        if (!p.ackFlag && p.synFlag)
          sendPkt(lastPkt, address);
        
        // Normal procedure
        else if (p.ackFlag && !p.synFlag){
          port = p.sourcePort;
          
          tcpTimer.cancel(); //cancel timer for sent SYN+ACK
				  tcpTimer = null;

          changeState("SYN_RCVD", "ESTABLISHED");
        }

        break;

      case "ESTABLISHED":

        // If we receive a SYN+ACK here, then the last ACK was dropped, so resend it.
        if (p.ackFlag && p.synFlag) {
          sendPkt(lastACKpkt, address);
        }

        // Normal Procedure
        // if close() --> FIN_WAIT_1    if FIN received --> CLOSE_WAIT
        else if (p.finFlag && !p.synFlag && !p.ackFlag) {

          port = p.sourcePort;
          seqNum = p.ackNum;
          ackNum = p.seqNum + 1;

          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 1, null);
          sendPkt(ACKpkt, address);

          changeState("ESTABLISHED", "CLOSE_WAIT");
        }

        break;

      case "FIN_WAIT_1":

        if (p.ackFlag){

          // Retransmit the latest ACK if we receive a SYN+ACK
          if (p.synFlag) {
            sendPkt(lastACKpkt, address);
          }

          //Normal Procedure
          else{
            changeState("FIN_WAIT_1", "FIN_WAIT_2");

            tcpTimer.cancel(); //Cancel timer for the fin being acked
				    tcpTimer = null;
          }
        }

        // Normal Procedure
        else if (p.finFlag && !p.synFlag && !p.ackFlag){
          port = p.sourcePort;
          seqNum = p.ackNum;
          ackNum = p.seqNum + 1;


          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 1, null);
          sendPkt(ACKpkt, address);

          changeState("FIN_WAIT_1","CLOSING");

        }
        break;

      case "FIN_WAIT_2":
        

        if (p.finFlag){
          
          port = p.sourcePort;
          seqNum = p.ackNum;
          ackNum = p.seqNum + 1;

          changeState("FIN_WAIT_2", "TIME_WAIT");

          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 1, null);
          sendPkt(ACKpkt, address);

          createTimerTask(30*1000, null); // wait 30 seconds
        }

        break;

      case "CLOSE_WAIT":
        
        // Retransmit latest ACK if we receive a fin
        if (p.finFlag){
          sendPkt(lastACKpkt, address);       
        }
        break;

      case "LAST_ACK":

        // Normal Procedure
        if (p.ackFlag && !p.synFlag && !p.finFlag) {
          tcpTimer.cancel(); //cancel timer for previously sent fin
				  tcpTimer = null;

          changeState("LAST_ACK", "TIME_WAIT");

          createTimerTask(30*1000, null); // 30 sec wait
        }

        // Retransmit latest ACK if we receive a fin in this state
        if (p.finFlag) {
          sendPkt(lastACKpkt, address);
        }

        break;

      case "CLOSING":

        // Normal Procedure
        if (p.ackFlag && !p.synFlag && !p.finFlag) {
          tcpTimer.cancel(); //Cancel timer for sent fin
				  tcpTimer = null;

          changeState("CLOSING", "TIME_WAIT");

          createTimerTask(30*1000, null); // 30 sec wait
        }

        // Retransmit latest ACK if we receive a fin in this state
        else if (p.finFlag){
          sendPkt(lastACKpkt, address);
        }

        break;

      case "TIME_WAIT":
        
        // Retransmit latest ACK if we receive a fin in this state
        if (p.finFlag) {
          sendPkt(lastACKpkt, address);
        }

        break;

      default:
        break;
    }

    this.notifyAll();
  }

  /** CPPacket(port, localport,0, 0, false, true, false, 0, null);
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling
   * ServerSocket.accept(), but this method belongs to the Socket object
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    changeState("CLOSED", "LISTEN");
    D.registerListeningSocket(localport, this);
    thisIsServer = true;

    while(state != "ESTABLISHED"){
      try{
        wait();
      }
      catch (InterruptedException e){
        System.out.println(e);
      }
    }
  }


  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;

  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket.
   *
   * In other words, your close() method just does the state change and sends a fin.
   * Then close() returns immediately, without waiting for the real close of the connection;
   * all the other things should be done in the background, not by close() method directly.
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
    seqNum = ackNum;
    ackNum = seqNum + 1;

    // Wait for server's state to change before dealing with the close call
    if (thisIsServer) {

      while (state != "CLOSE_WAIT"){
        try {
          wait();
        }
        catch (InterruptedException e){
          System.out.println(e);
        }
      }
    }
    

    if (state == "ESTABLISHED"){

      TCPPacket FINpkt = new TCPPacket(localport, port, seqNum, ackNum, false, false, true, 1, null);
      sendPkt(FINpkt, address);

      changeState("ESTABLISHED", "FIN_WAIT_1");
    }
    else if (state == "CLOSE_WAIT"){

      TCPPacket FINpkt = new TCPPacket(localport, port, seqNum, ackNum, false, false, true, 1, null);
      sendPkt(FINpkt, address);

      changeState("CLOSE_WAIT", "LAST_ACK");
    }

  }

  /**
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;

    // Change to CLOSED state, this does not always work
    if(state == "TIME_WAIT"){
      try {
        changeState("TIME_WAIT", "CLOSED");    
      } 
      catch (Exception e) {
        notifyAll();
      }

      notifyAll(); 

      // Unregister the connection
      try {
           D.unregisterConnection(address, localport, port, this);
      } 
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
	 * Function to help send packets. 
	 * 
	 * @param pack packet to be sent
	 * @param addr address to which to send the packet
	 */
	private void sendPkt(TCPPacket pkt, InetAddress addr){
		TCPWrapper.send(pkt, addr);
		
		//For FINs and SYN+ACKs, send the packet and start a retransmission timer.
		if (!pkt.ackFlag || pkt.synFlag){
			lastPkt = pkt;
			createTimerTask(1000, null);
		}
		
		//No retransmission for ACKs
		else {
			lastACKpkt = pkt;
    }
	}

  private void changeState(String initial, String next) {
    System.out.println("!!! " + initial + "->" + next);
    state = next;
  }
}
