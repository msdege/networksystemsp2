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

  private String state = "CLOSED";

  private String[] possibleStates = {"CLOSED", "SYN_SENT", "LISTEN", "SYN_RCVD", "ESTABLISHED",
    "FIN_WAIT_1", "CLOSE_WAIT", "FIN_WAIT_2", "LAST_ACK", "CLOSING", "TIME_WAIT"}; // for reference 


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

    D.registerConnection(address, localport, port,this);

    TCPPacket SYNpkt = new TCPPacket(port, localport,0, 0, false, true, false, 0, null);
    TCPWrapper.send(SYNpkt, address);

    System.out.println("Client connect call\n");
    changeState("CLOSED", "SYN_SENT");

  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   * 
   *  receivePacket() is going to be one big switch statement on the state of the connection. 
   *  Add the case statement for LISTEN and have it send a SYN+ACK when it receives a SYN. 
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println("pkt received\n");
  	System.out.println(p.toString());
    
    int seqNum;
    int ackNum;

    this.notifyAll();

    switch (state) {
      case "LISTEN":
        System.out.println("Current state: "+state+"\n");

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

        TCPPacket SYNACKpkt = new TCPPacket(port, localport,seqNum, ackNum, true, true, false, 0, null);
        TCPWrapper.send(SYNACKpkt, address);

        changeState("LISTEN", "SYN_RCVD");
      
        break;
    
      case "SYN_SENT":
        System.out.println("Current state: "+state+"\n");

        seqNum = p.ackNum;
        ackNum = p.seqNum + 1;

        if (p.ackFlag && p.synFlag){
          TCPPacket ACKpkt = new TCPPacket(port, localport,seqNum, ackNum, true, false, false, 0, null);
          TCPWrapper.send(ACKpkt, address);

          changeState("SYN_SENT", "ESTABLISHED");
        }
        
        break;

      case "SYN_RCVD":
        System.out.println("Current state: "+state+"\n");

        if (p.ackFlag && !p.synFlag){
          changeState("SYN_RCVD", "ESTABLISHED");
        }

        break;

      default:
        break;
    }
  }
  
  /** CPPacket(port, localport,0, 0, false, true, false, 0, null);
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling 
   * ServerSocket.accept(), but this method belongs to the Socket object 
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    state = "Listen";
    System.out.println("Server acceptConnection(). In listening state.\n");

    D.registerListeningSocket(localport, this);
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
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
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
    System.out.println("STATE CHANGE: " + initial + "-->" + next);
    state = next;
  }
}
