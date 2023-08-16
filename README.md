# TCP State Machine
I implemented a base Socket for a Transmission Control Protocol state machine to allow Java applications to open and close and send packets from client to server.
This implementation of a socket can tolerate packet loss of up to 20%.

Here is a diagram depicting the different states from when the machine is first executed to the final closing of the machine:
![alt text](https://github.com/msdege/networksystemsp2/blob/main/StateMachine.PNG?raw=true)

Here is a diagram depicting the successful execution of the code and the subsequent packets sent from server to client and vice versa:
![alt text](https://github.com/msdege/networksystemsp2/blob/main/tests 1-4 annotation.jpg?raw=true)
The green left side depicts the server and the red right side depicts the client. The server is started first followed by the client.
(source: project files provided by Professor Zhou)
