import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;
  private int ackNum;
  private int seqNum;

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

    D.registerConnection(address, localport, port, this);

    TCPPacket SYNpkt = new TCPPacket(localport, port, 10, 1, false, true, false, 1, null);
    TCPWrapper.send(SYNpkt, address);

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
  	System.out.println("Packet Received: " + p.toString());

    switch (state) {
      case "LISTEN":

        port = p.sourcePort;
        seqNum = p.ackNum; 
        ackNum = p.seqNum + 1;

        if (!p.synFlag || p.ackFlag) {
          break;
        }
          
        try {
          D.unregisterListeningSocket(localport, this);
          D.registerConnection(p.sourceAddr, localport, port, this);
        }
        catch (Exception e) {
          System.out.println(e);
        }

        TCPPacket SYNACKpkt = new TCPPacket(localport, port, seqNum, ackNum, true, true, false, 0, null);
        TCPWrapper.send(SYNACKpkt, p.sourceAddr);

        changeState("LISTEN", "SYN_RCVD");
      
        break;
    
      case "SYN_SENT":

        if (p.ackFlag && p.synFlag){
          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 0, null);
          TCPWrapper.send(ACKpkt, address);

          changeState("SYN_SENT", "ESTABLISHED");
        }
        
        break;

      case "SYN_RCVD":

        if (p.ackFlag && !p.synFlag){
          changeState("SYN_RCVD", "ESTABLISHED");
        }

        break;

      case "ESTABLISHED":
        // if close() --> FIN_WAIT_1    if FIN received --> CLOSE_WAIT
        if (p.finFlag) {
          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 0, null);
          TCPWrapper.send(ACKpkt, address);

          changeState("ESTABLISHED", "CLOSE_WAIT");
        }
        break;

      case "FIN_WAIT_1":
        if (p.ackFlag && !p.synFlag && !p.finFlag){
          changeState("FIN_WAIT_1", "FIN_WAIT_2");
        }
        break;

      case "FIN_WAIT_2":
        if (p.finFlag && !p.synFlag && !p.ackFlag){
          TCPPacket ACKpkt = new TCPPacket(localport, port, -2, p.ackNum, true, false, false, 0, null);
          TCPWrapper.send(ACKpkt, address);

          changeState("FIN_WAIT_2", "TIME_WAIT");
        }
        break;

      case "CLOSE_WAIT":
        System.out.println("WE SHOULD NOT BE HERE!!!");
        break;
      
      case "LAST_ACK":
        if (p.ackFlag && !p.synFlag && !p.finFlag) {
          changeState("LAST_ACK", "TIME_WAIT");
        }
        break;
      
      case "CLOSING":
        break;

      case "TIME_WAIT":
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

    while(state != "ESTABLISHED" && state != "SYN_RCVD"){
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

      if (state == "ESTABLISHED"){
        changeState("ESTABLISHED", "FIN_WAIT_1");
      }
      else if (state == "CLOSE_WAIT"){
        changeState("CLOSE_WAIT", "LAST_ACK");
      }

      TCPPacket FINpkt = new TCPPacket(localport, port, seqNum, ackNum, false, false, true, 0, null);
      TCPWrapper.send(FINpkt, address);

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
  }

  private void changeState(String initial, String next) {
    System.out.println("!!! " + initial + "->" + next);
    state = next;
  }
}
