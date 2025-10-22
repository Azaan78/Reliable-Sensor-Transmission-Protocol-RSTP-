/*
 * Replace the following string of 0s with your student number
 * c4029726
 */
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.File;
//Added imports
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.ArrayList;



public class Protocol {

	static final String  NORMAL_MODE="nm"   ;         // normal transfer mode: (for Part 1 and 2)
	static final String	 TIMEOUT_MODE ="wt"  ;        // timeout transfer mode: (for Part 3)
	static final String	 LOST_MODE ="wl"  ;           // lost Ack transfer mode: (for Part 4)
	static final int DEFAULT_TIMEOUT =1000  ;         // default timeout in milliseconds (for Part 3)
	static final int DEFAULT_RETRIES =4  ;            // default number of consecutive retries (for Part 3)
	public static final int MAX_Segment_SIZE = 4096;  //the max segment size that can be used when creating the received packet's buffer

	/*
	 * The following attributes control the execution of the transfer protocol and provide access to the 
	 * resources needed for the transfer 
	 * 
	 */ 

	private InetAddress ipAddress;      // the address of the server to transfer to. This should be a well-formed IP address.
	private int portNumber; 		    // the  port the server is listening on
	private DatagramSocket socket;      // the socket that the client binds to

	private File inputFile;            // the client-side CSV file that has the readings to transfer  
	private String outputFileName ;    // the name of the output file to create on the server to store the readings
	private int maxPatchSize;		   // the patch size - no of readings to be sent in the payload of a single Data segment

	private Segment dataSeg   ;        // the protocol Data segment for sending Data segments (with payload read from the csv file) to the server 
	private Segment ackSeg  ;          // the protocol Ack segment for receiving ACK segments from the server

	private int timeout;              // the timeout in milliseconds to use for the protocol with timeout (for Part 3)
	private int maxRetries;           // the maximum number of consecutive retries (retransmissions) to allow before exiting the client (for Part 3)(This is per segment)
	private int currRetry;            // the current number of consecutive retries (retransmissions) following an Ack loss (for Part 3)(This is per segment)

	private int fileTotalReadings;    // number of all readings in the csv file
	private int sentReadings;         // number of readings successfully sent and acknowledged
	private int totalSegments;        // total segments that the client sent to the server

    private Scanner fileScanner;      // Created by student to ensure CVS file can be readn and make packets after first packet full

	// Shared Protocol instance so Client and Server access and operate on the same values for the protocol’s attributes (the above attributes).
	public static Protocol instance = new Protocol();

	/**************************************************************************************************************************************
	 **************************************************************************************************************************************
	 * For this assignment, you have to implement the following methods:
	 *		sendMetadata()
	 *      readandSend()
	 *      receiveAck()
	 *      startTimeoutWithRetransmission()
	 *		receiveWithAckLoss()
	 * Do not change any method signatures, and do not change any other methods or code provided.
	 ***************************************************************************************************************************************
	 **************************************************************************************************************************************/
	/* 
	 * This method sends protocol metadata to the server.
	 * See coursework specification for full details.	
	 */
	public void sendMetadata() {
        //Sets variable to read file to null (nothing)
        Scanner Reading = null;
        //Tries if a file is read
		try{
            //Reads input file
            Reading = new Scanner(inputFile);
            //Loops until there are no next lines and counts current amout of lines (count starts from 1 not 0)
            while (Reading.hasNextLine()) {
                fileTotalReadings++;
                Reading.nextLine();
            }
        }
        //Error returned if no file found
        catch(FileNotFoundException e){
            //Message printed in terminal and error message printed too
            System.out.println("CLIENT ERROR: Error reading metadata file");
            e.printStackTrace();
            return;
        }
        //If something still saved in Reading variable then the variable is closed as not needed
        finally{
            if(Reading!=null){
                Reading.close();
            }
        }
        //Concatenating payload string and instantiating segment object
        String payload = (fileTotalReadings + "," + outputFileName + "," + maxPatchSize);
        Segment metaSeg = new Segment(0,SegmentType.Meta,payload,payload.length());
        System.out.println("Sending Metadata Request");

        //Tries to send packet
        try{
            //Creates baos and oos to send objects to sockets in data packets
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            //Writes object (metaseg) and turns into oos so can be send in a packet
            oos.writeObject(metaSeg);
            oos.flush();
            //Converts baos to bytesArray to use in packet
            byte[] sendBytes = baos.toByteArray();
            java.net.DatagramPacket packet = new java.net.DatagramPacket(
                    sendBytes,
                    sendBytes.length,
                    ipAddress,
                    portNumber
            );
            //Sends packet to socket and returns message in terminal
            socket.send(packet);
            System.out.println("CLIENT: Meta data segment sent to server");
            oos.close();
            baos.close();
        }
        //If packet cannot be found or sent message returned
        catch(java.io.IOException e){
            System.out.println("CLIENT ERROR: Failed to send meta data");
            e.printStackTrace();
        }
        //MetaSegment();
        }


	/* 
	 * This method read and send the next data segment (dataSeg) to the server. 
	 * See coursework specification for full details.
	 */
	public void readAndSend() {
        try{
            //Reads input file and creates list to store payload's if csv lines are bigger than patch size
            if (fileScanner == null) {
                fileScanner = new Scanner(inputFile);
            }
            //Declares payload to save currently read lines and patchcount to count to ensure maxPatch isn't met
            String Payload = "";
            int PatchCount = 0;
            //Loops for max patch size
            while (fileScanner.hasNextLine() && maxPatchSize > PatchCount){
                //Count increments by 1, reads current line and concatenates to payload
                PatchCount++;
                String CurLine = fileScanner.nextLine().trim();
                Payload = Payload + (CurLine);
                //If there are other records seperate with ";"
                if (fileScanner.hasNextLine()) {
                    Payload = Payload + (";");
                }
            }
            //Instantiates a new segment and displays message
            Segment ReadSeg = new Segment((totalSegments + 1),SegmentType.Data,Payload,Payload.length());
            System.out.println("CLIENT: Preparing datapacket for segment number " + (totalSegments+1));

            this.dataSeg = ReadSeg;

            try{
                //Converts ReadSeg to baos and oos to send in packet to socket
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
                oos.writeObject(ReadSeg);
                oos.flush();
                byte[] bytesToSend = baos.toByteArray();
                java.net.DatagramPacket packet = new java.net.DatagramPacket(
                        bytesToSend,
                        bytesToSend.length,
                        ipAddress,
                        portNumber
                );
                System.out.println("CLIENT: Sending datapacket for segment number " + (totalSegments + 1));
                socket.send(packet);
                totalSegments++;
                System.out.println("CLIENT: Sending packet number: " + (totalSegments));
                System.out.println("CLIENT: Packet length: " + packet.getLength());
                System.out.println("CLIENT: Packet payload: " + packet.getAddress());
                System.out.println("CLIENT: Packet port: " + packet.getPort());
                System.out.println("CLIENT: Packet payload: " + ReadSeg.getPayLoad());
                System.out.print("------------------------------------------------------------------\n"
                        + "------------------------------------------------------------------\n");
                oos.close();
                baos.close();
            }
            catch(java.io.IOException e){
                System.out.println("CLIENT ERROR: Failed to send data");
                e.printStackTrace();
            }
        }
        //If there is an issue reading the file
        catch(java.io.FileNotFoundException e){
            System.out.println("CLIENT ERROR: Error reading file");
            e.printStackTrace();
            return;
        }
    }


	/* 
	 * This method receives the current Ack segment (ackSeg) from the server 
	 * See coursework specification for full details.
	 */
	public boolean receiveAck() {
        //Defining expectedSeq to see what sequence number we should be on, if we don't know what it is we set it to -1 as a placeholder
        int expectedSeq;
        if (dataSeg != null) {
            expectedSeq = dataSeg.getSeqNum();
        }
        else {
            expectedSeq = -1;
        }
        //Checks to see if all Segments have been read
        if(sentReadings >= fileTotalReadings){
            System.out.println("CLIENT: All segments acknowledged, total checked segments is " + totalSegments);
            System.exit(0);
            return true;
        }
        //Prints in terminal message to let user know the current segment, then tries to see if current segment is ackknowledged
        System.out.println("CLIENT: Waiting for ACK signal number " + dataSeg.getSeqNum());

        try{
            //Makes temp store for bytes from max segment then receives packet
            System.out.println("CLIENT: Checking ACK");
            byte[] tempPacketStore = new byte[MAX_Segment_SIZE];
            java.net.DatagramPacket ackPacket = new java.net.DatagramPacket(tempPacketStore, tempPacketStore.length);
            socket.receive(ackPacket);
            System.out.println("CLIENT: packet sent");
            //Packet to segment object data
            int len = ackPacket.getLength();
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(ackPacket.getData(), 0, len);
            java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);

            //Converting stream data to the actual object
            Segment ack = null;
            try {
                ack = (Segment) ois.readObject();
            }
            catch (ClassNotFoundException cnfe) {
                System.out.println("CLIENT ERROR: Received an object of unknown class");
                cnfe.printStackTrace();
                ois.close();
                return false;
            }

            System.out.println("CLIENT: ACK received - SEQ#" + ack.getSeqNum());

            //Check sequence number
            if (ack.getSeqNum() != expectedSeq) {
                System.out.println("CLIENT: Received unexpected ACK seq (expected " + expectedSeq + " but got " + ack.getSeqNum() + ")");
                try { ois.close(); } catch (Exception ex) {}
                return false;
            }

            // update sentReadings by counting readings in the payload of the acknowledged data segment
            if (dataSeg != null && dataSeg.getPayLoad() != null && dataSeg.getPayLoad().length() > 0) {
                String[] readings = dataSeg.getPayLoad().split(";");
                sentReadings += readings.length;
            }

            System.out.println("CLIENT: ACK matched. Total readings acknowledged so far: " + sentReadings);

            // if all readings acknowledged -> exit as per spec
            if (sentReadings >= fileTotalReadings) {
                System.out.println("CLIENT: All segments acknowledged, total checked segments is " + totalSegments);
                System.exit(0);
            }

            //FINISH CHECK

            bais.close();
            ois.close();
            return true;
        }
        catch(Exception e){
            System.out.println("CLIENT ERROR: Failed to receive ACK signal number");
            e.printStackTrace();
        }
		return false;
	}

	/* 
	 * This method starts a timer and does re-transmission of the Data segment 
	 * See coursework specification for full details.
	 */
	public void startTimeoutWithRetransmission()   {  
		System.exit(0);
	}


	/* 
	 * This method is used by the server to receive the Data segment in Lost Ack mode
	 * See coursework specification for full details.
	 */
	public void receiveWithAckLoss(DatagramSocket serverSocket, float loss)  {
		System.exit(0);
	}


	/*************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	These methods are implemented for you .. Do NOT Change them 
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************
	 **************************************************************************************************************************************/	 
	/* 
	 * This method initialises ALL the 14 attributes needed to allow the Protocol methods to work properly
	 */
	public void initProtocol(String hostName , String portNumber, String fileName, String outputFileName, String batchSize) throws UnknownHostException, SocketException {
		instance.ipAddress = InetAddress.getByName(hostName);
		instance.portNumber = Integer.parseInt(portNumber);
		instance.socket = new DatagramSocket();

		instance.inputFile = checkFile(fileName); //check if the CSV file does exist
		instance.outputFileName =  outputFileName;
		instance.maxPatchSize= Integer.parseInt(batchSize);

		instance.dataSeg = new Segment(); //initialise the data segment for sending readings to the server
		instance.ackSeg = new Segment();  //initialise the ack segment for receiving Acks from the server

		instance.fileTotalReadings = 0; 
		instance.sentReadings=0;
		instance.totalSegments =0;

		instance.timeout = DEFAULT_TIMEOUT;
		instance.maxRetries = DEFAULT_RETRIES;
		instance.currRetry = 0;		 
	}


	/* 
	 * check if the csv file does exist before sending it 
	 */
	private static File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("CLIENT: File does not exists"); 
			System.out.println("CLIENT: Exit .."); 
			System.exit(0);
		}
		return file;
	}

	/* 
	 * returns true with the given probability to simulate network errors (Ack loss)(for Part 4)
	 */
	private static Boolean isLost(float prob) 
	{ 
		double randomValue = Math.random();  //0.0 to 99.9
		return randomValue <= prob;
	}

	/* 
	 * getter and setter methods	 *
	 */
	public String getOutputFileName() {
		return outputFileName;
	} 

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	} 

	public int getMaxPatchSize() {
		return maxPatchSize;
	} 

	public void setMaxPatchSize(int maxPatchSize) {
		this.maxPatchSize = maxPatchSize;
	} 

	public int getFileTotalReadings() {
		return fileTotalReadings;
	} 

	public void setFileTotalReadings(int fileTotalReadings) {
		this.fileTotalReadings = fileTotalReadings;
	}

	public void setDataSeg(Segment dataSeg) {
		this.dataSeg = dataSeg;
	}

	public void setAckSeg(Segment ackSeg) {
		this.ackSeg = ackSeg;
	}

	public void setCurrRetry(int currRetry) {
		this.currRetry = currRetry;
	}

}
