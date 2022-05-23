package SimpleServer;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.File;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;
import FileUtils.FileNameParser;

class requestInfo{
	private String[] requestTokens;
	private String requestLine,completeRequest;
	private BufferedReader requestReader;
	private boolean partial;
	private long startval;
	private long endval;
	public requestInfo(Socket connection)throws Exception{
		this.requestReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		this.requestLine = requestReader.readLine();
		this.partial = false;
		if(this.requestLine==null ||  !(this.requestLine.contains(" "))){
			this.requestTokens = new String[1];
			requestTokens[0] = "";
		}
		else{
			this.completeRequest = "";
			this.completeRequest+= requestLine+"\n";
			String temp;String[] temp1;
			while((temp = this.requestReader.readLine()).length()!=0 && temp!=null){
				this.completeRequest+=temp+"\n";
				if(temp.contains("Range:")){
					this.partial = true;
					temp1 = (temp.split(": ")[1].split("=")[1]).split("-");
					if(temp1.length==1){
						this.startval = Long.parseLong(temp1[0]);
						this.endval = 0;
					}
					else if(temp1.length==2){
						this.startval = Long.parseLong(temp1[0]);
						this.endval = Long.parseLong(temp1[1]);
					}
					else{
						this.partial = false;
					}
				}
			}
			this.requestTokens = this.requestLine.split(" ");
		}
	}
	public boolean verifyRequest(){
		if(this.requestTokens.length==3){
			if(this.requestTokens[0].equals("GET") && (this.requestTokens[2].equals("HTTP/1.1") || this.requestTokens[2].equals("HTTP/1.0"))){
				return true;
			}
			else{
				return false;
			}
		}
		else{
			return false;
		}
	}
	public String getRequestLine(){
		return this.requestLine;
	}
	public String getMethod(){
		return this.requestTokens[0];
	}
	public String getURI(){
		return this.requestTokens[1];
	}
	public String getCompleteRequest(){
		return this.completeRequest;
	}
	public long get_startval(){
		return this.startval;
	}
	public long get_endval(){
		return this.endval;
	}
	public boolean isPartial(){
		return this.partial;
	}
}

public class SimpleServer{
	private ServerSocket serverconnection;
	private List<Socket> currentconnections;
	private List<Integer> wasteConnectionIDs;
	private int portNo,noOfThreads,threadLimit,backlog;
	private servingJob servingJobObj;
	private startingJob startserv;
	private ExecutorService mainExecutor,threadExecutor;
	private String ipAddress,dirPath,fileNotFoundMsg,invalidRequestMsg;
	private boolean serverstatus,quiet;
	public SimpleServer(String ipAddress,int portNo,String dirPath, int threadLimit,int backlog)throws IllegalArgumentException{
		try{
			InetAddress.getByName(ipAddress);
		}
		catch(Exception e){
			throw new IllegalArgumentException("No such IP Address - "+ipAddress);
		}
		if(portNo>=65536 || portNo<=0){
			throw new IllegalArgumentException("No such Port No - "+portNo);
		}
		if(!(new File(dirPath)).exists()){
			throw new IllegalArgumentException("No such directory - "+dirPath);
		}
		if(threadLimit>200){
			throw new IllegalArgumentException("Invalid Thread Limit - "+threadLimit+"... Maximum Thread Limit is 200");
		}
		if(threadLimit<=0){
			throw new IllegalArgumentException("Invalid thread limit - "+threadLimit);
		}
		if(backlog<0){
			throw new IllegalArgumentException("Invalid Backlog - "+backlog);
		}
		if(backlog>50){
			throw new IllegalArgumentException("Invalid Backlog - "+backlog+"... Maximum Backlog is 50");
		}
		this.ipAddress = ipAddress;
		this.portNo = portNo;
		this.dirPath = dirPath;
		this.threadLimit = threadLimit;
		this.backlog = backlog;
		this.quiet = false;
		this.serverstatus = false;
		this.fileNotFoundMsg = "No such thing Found";
		this.invalidRequestMsg = "Not a valid Request";
		this.noOfThreads = 0;
	}
	private class servingJob implements Runnable{
		private Socket connection;
		private SimpleServer server;
		servingJob(SimpleServer server,Socket connection){
			this.server = server;
			this.connection = connection;
		}
		@Override
		public void run(){
			this.server.serve(this.connection);
		}
	}
	private class startingJob implements Runnable{
		SimpleServer server;
		startingJob(SimpleServer server){
			this.server = server;
		}
		@Override
		public void run(){
			try{
				this.server.startserving();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	public void start(){
		if(!(this.serverstatus)){
			try{
				this.serverconnection = new ServerSocket(this.portNo,this.backlog,InetAddress.getByName(this.ipAddress));
				this.mainExecutor = Executors.newFixedThreadPool(1);
				this.threadExecutor = Executors.newFixedThreadPool(this.threadLimit);
				this.startserv = new startingJob(this);
				this.serverstatus = true;
				this.mainExecutor.execute(this.startserv);
				this.currentconnections = new ArrayList<Socket>();
				this.wasteConnectionIDs = new ArrayList<Integer>();
				this.noOfThreads = 0;
			}
			catch(Exception e){
				System.out.println("Unable to start server...");
				e.printStackTrace();
				this.stop();
			}
			System.gc();
		}
		else{
			if(!this.quiet){System.out.println("Already Running!");}
		}
	}
	private void startserving()throws Exception{
		if(!this.get_quiet()){
			System.out.println("Server Started");
		}
		this.serverstatus = true;
		while(this.serverstatus){
			while(this.noOfThreads<this.threadLimit){
				//System.out.println("Waiting to accept connection...");
				try{
					this.servingJobObj = new servingJob(this,this.serverconnection.accept());
				}
				catch(Exception e){
					if(this.serverstatus){
						throw e;
					}
				}
				//System.out.println("Connection accepted - "+this.noOfThreads);
				this.noOfThreads++;
				try{
					this.threadExecutor.execute(this.servingJobObj);
				}
				catch(Exception e){
					if(this.serverstatus){
						throw e;
					}
				}
			}
			System.out.print("");
		}
	}
	private void serve(Socket connection){
		int index = -1;
		try{
			//System.out.println("Reusable connection IDs before serving - "+ wasteConnectionIDs);
			if(this.wasteConnectionIDs.size()==0){
				index = this.currentconnections.size();
				this.currentconnections.add(connection);
			}
			else{
				this.currentconnections.set(this.wasteConnectionIDs.get(this.wasteConnectionIDs.size()-1),connection);
				index = this.wasteConnectionIDs.get(this.wasteConnectionIDs.size()-1);
				this.wasteConnectionIDs.remove(this.wasteConnectionIDs.size()-1);
			}
			System.out.println("Opening thread - "+index);
			requestInfo request = new requestInfo(connection);
			if(request.verifyRequest()){
				File fileToServe = new File(this.dirPath + (request.getURI().equals("/")?"/index.html":request.getURI()));
				if(fileToServe.exists()){
					System.out.println("\n"+request.getCompleteRequest());
					if(request.isPartial()){
						System.out.println("Requested data - from "+request.get_startval()+" bytes to "+request.get_endval()+" bytes");
						if(request.get_endval()==0){
							SimpleServer.writeResponse(connection,fileToServe,1024,true,request.get_startval(),fileToServe.length()-1);
						}
						else{
							SimpleServer.writeResponse(connection,fileToServe,1024,true,request.get_startval(),request.get_endval());
						}
					}
					else{
						SimpleServer.writeResponse(connection,fileToServe,1024,true,0,fileToServe.length()-1);
					}
					if(!quiet){
						System.out.println("Served "+fileToServe.getAbsolutePath());
					}
				}
				else{
					if(!quiet){
						System.out.println("No such File - "+fileToServe.getAbsolutePath());
					}
					SimpleServer.writeResponse(connection,"<h1>" + this.fileNotFoundMsg + "</h1>",true);
				}
			}
			else{
				if(!quiet){
					System.out.println("Invalid request - "+ request.getRequestLine());
				}
				SimpleServer.writeResponse(connection,"<h1>" + this.invalidRequestMsg + "</h1>",true);
			}
			System.gc();
		}
		catch(Exception e){
			if(this.serverstatus){
				System.out.println("Unable to serve requested file...");
				e.printStackTrace();
				System.gc();
			}
		}
		try{
			this.currentconnections.get(index).close();
		}
		catch(Exception e){
			if(this.serverstatus){
				System.out.println("Unable to close thread properly...");
				e.printStackTrace();
			}
		}
		try{
			this.currentconnections.set(index,null);
			this.wasteConnectionIDs.add(index);
			this.noOfThreads--;
			System.out.println("Closing thread - "+index);
		}
		catch(Exception e){
			if(this.serverstatus){
				System.out.println("Unable to manage threads properly...");
				e.printStackTrace();
			}
		}
		//System.out.println("NoOfThreads after serving -"+this.noOfThreads);
		//System.out.println("Reusable Connections after serving - "+this.wasteConnectionIDs);
	}
	public void stop(){
		try{
			if(this.serverstatus){
				this.serverstatus = false;
				if(this.threadExecutor!=null){
					this.threadExecutor.shutdown();
					while(!this.threadExecutor.isShutdown()){
						if(!quiet){
							System.out.println("Trying To Shutdown Serving Thread...");
						}
						this.threadExecutor.awaitTermination(3,TimeUnit.SECONDS);
					}
				}
				if(this.mainExecutor!=null){
					this.mainExecutor.shutdown();
					while(!this.mainExecutor.isShutdown()){
						if(!quiet){
							System.out.println("Trying To Shutdown Main Thread...");
						}
						this.mainExecutor.awaitTermination(3,TimeUnit.SECONDS);
					}
				}
				if(this.serverconnection != null){
					this.serverconnection.close();
				}
				for(int i=0;i<this.currentconnections.size();i++){
						if(currentconnections.get(i)!=null){
						currentconnections.get(i).close();
					}
				}
				this.startserv = null;
				this.servingJobObj = null;
				this.mainExecutor = null;
				this.threadExecutor = null;
				this.serverconnection = null;
				System.gc();
				if(!this.quiet){ 
					System.out.println("Server stopped");
				}
			}
			else{
				if(!this.quiet){ 
					System.out.println("Server has already stopped/not yet started...");
				}
			}
		}
		catch(Exception e){
			System.out.println("Unable to stop server properly...");
			e.printStackTrace();
			System.gc();
		}
	}
	public static void writeResponse(Socket connection, File fileToServe,int buffersize,boolean header,long startval,long endval)throws Exception{
		if(endval>fileToServe.length()){
			endval = fileToServe.length();
		}
		if(startval>fileToServe.length() || startval<0){
			startval = 0;
		}
		if(startval>=endval){
			startval = 0;
			endval = fileToServe.length();
		}
		OutputStream responseStream = connection.getOutputStream();
		FileInputStream servablefile = new FileInputStream(fileToServe);
		long filelength = fileToServe.length();
		long contentlength = endval - startval + 1;
		if(header){
			if(filelength == contentlength){
				responseStream.write("HTTP/1.1 200 OK\n".getBytes());
				responseStream.write("Accept-Ranges: none\n".getBytes());
				responseStream.write(("Content-type: "+ (new FileNameParser(fileToServe.getName()).getContentType())+"\n").getBytes());
				responseStream.write(("Content-length: "+filelength+"\n\n").getBytes());
			}
			else{
				responseStream.write("HTTP/1.1 206 Partial Content\n".getBytes());
				responseStream.write(("Content-Type: "+ (new FileNameParser(fileToServe.getName()).getContentType())+"\n").getBytes());
				responseStream.write("Accept-Ranges: bytes\n".getBytes());
				responseStream.write(("Content-Range: bytes "+startval+"-"+endval+"/"+filelength+"\n").getBytes());
				responseStream.write(("Content-length: "+contentlength+"\n\n").getBytes());
			}
		}
		if(filelength==contentlength){
			int length = 0;
			byte[] buffer = new byte[buffersize];
			while((length=servablefile.read(buffer))>0){
				responseStream.write(buffer,0,length);
			}
		}
		else{
			byte[] buffer = new byte[buffersize];
			servablefile.skip(startval);
			long extra = contentlength % ((long) buffersize);
			contentlength -= extra;
			long servedlength = 0;
			while((servedlength+=servablefile.read(buffer)) <= contentlength){
				responseStream.write(buffer,0,buffersize);
			}
			responseStream.write(buffer,0,(int)extra);
		}
	}
	public static void writeResponse(Socket connection, String msg,boolean header)throws Exception{
		OutputStream responseStream = connection.getOutputStream();
		if(header){
			responseStream.write(("HTTP/1.1 200 OK\nContent-type: text/html;charset=UTF-8\nContent-length: "+msg.length()+"\n\n").getBytes());
		}
		responseStream.write(msg.getBytes());
	}
	public String get_ipAddress(){
		return this.ipAddress;
	}
	public int get_portNo(){
		return this.portNo;
	}
	public int get_threadLimit(){
		return this.threadLimit;
	}
	public int get_backlog(){
		return this.backlog;
	}
	public boolean get_serverstatus(){
		return this.serverstatus;
	}
	public String get_fileNotFoundMsg(){
		return this.fileNotFoundMsg;
	}
	public void set_fileNotFoundMsg(String fileNotFoundMsg){
		this.fileNotFoundMsg = fileNotFoundMsg;
	}
	public String get_invalidRequestMsg(){
		return this.invalidRequestMsg;
	}
	public void set_invalidRequestMsg(String invalidRequestMsg){
		this.invalidRequestMsg = invalidRequestMsg;
	}
	public String get_dirPath(){
		return this.dirPath;
	}
	public void set_dirPath(String dirPath){
		this.dirPath = dirPath;
	}
	public boolean get_quiet(){
		return this.quiet;
	}
	public void set_quiet(boolean quiet){
		this.quiet = quiet;
	}
}
