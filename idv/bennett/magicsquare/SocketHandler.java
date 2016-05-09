
package idv.bennett.magicsquare;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;



/**
 * @author bennett
 *
 */
class SocketHandler 
{
	private __ServerSocket ss;
	private __ClientSocket cs;
	SocketHandler(){
		this.ss = null;
		this.cs = null;
	}
	boolean startSs(int sp, MagicSquare _ms){
		if(ss == null){
			ss = __ServerSocket.getInstance(sp, _ms, this);
			if(ss == null){
				return false;
			}
			ss.startServer();
		} else {
			System.out.println("server exist already.");
			return false;
		}
		return true;
	}
	void stopSs(){
		if(ss != null){
			ss.closeServer();
			this.ss = null;
		}
	}
	boolean startCs(InetAddress _ia, int _port, MagicSquare _ms){
		if(cs == null){
			cs = __ClientSocket.getInstance(_ia, _port, _ms, this);
			if(cs == null){
				return false;
			}
			cs.startClient();
		} else {
			System.out.println("Client Socket has existed already...");
			return false;
		}
		return true;
	}
	void stopCs(){
		if(cs != null){
			cs.closeClient();
			this.cs = null;
		}
	}
	void stopAll(){
		stopSs();
		stopCs();
	}
	/*void ssSend(byte[] array){
		ss.send(array);
	}*/
	void notifyCs(ByteBuffer bb){
		cs.__notify(bb);
	}
	void notifySs(ByteBuffer bb){
		ss.__notify(bb);
	}
}

class __ServerSocket extends Thread
{
	private int port;
	private MagicSquare ms;
	private ServerSocket ss;
	private Socket socket;
	private ByteBuffer data;
	private Timer timer;
	private Integer tick;
	private static __JDialog dialog;
	private SocketHandler sh;
	//private Object lock;
	static{
		dialog = null;
	}
	
	private __ServerSocket(){}
	private __ServerSocket(int _port, MagicSquare _ms, SocketHandler _sh){
		port = _port;
		ms = _ms;
		try {
			ss = new ServerSocket(port);
		} catch (BindException be) {
			System.out.println("BindException "+be.getMessage());
			port = -1;
		} catch (IOException e) {
			e.printStackTrace();
			port = -1;
		}
		if(port == -1){
			System.out.println("cant get ServerSocket...");
			return;
		}
		timer = new Timer();
		tick = 0;
		socket = null;
		this.sh = _sh;
		this.data = ByteBuffer.allocate(3);
		//this.lock = new Object();
	}
	static __ServerSocket getInstance(int _port, MagicSquare _ms, SocketHandler _sh){
		if(dialog == null){
			dialog = new __JDialog(_ms.getJFrame(), "Wait for client", "Wait for client...");
		}
		__ServerSocket ret = new __ServerSocket(_port, _ms, _sh);
		if(ret.port == -1){
			return null;
		}
		return ret;
	}
	
	void startServer(){
		timer.schedule(new UpdateSecCounter(dialog, tick), 1000, 1000);
		this.start();
		
		ms.getJDialog().setVisible(false);
		dialog.update(this, "Wait for client...");
		dialog.setVisible(true);
	}
	/*void send(byte array[]){
		
	}*/
	
	public void run()
	{
		try {
			socket = ss.accept();
		} catch (SocketException se) {
			// normal shutdown.
			System.out.println("normal shutdowm.."+ se.getMessage());
			//closeServer(); from cancel button.
			return ;
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("un normal exception... network problem?");
			dialog.setVisible(false);
			closeServerFromSocketHandler();
			ms.setStatus("Connect Fail");
			return ;
		}
		
		timer.cancel();
		timer = null;
		dialog.setVisible(false);
		ms.setStatus("Connect Success");
		
		SocketStream stream = new SocketStream();
		boolean r = stream.newBos(socket);
		if(r == false){
			closeServerFromSocketHandler();
			ms.setStatus("Output Fail");
			return ;
		}
		r = stream.newBis(socket);
		if(r == false){
			closeServerFromSocketHandler();
			ms.setStatus("Input Fail");
			stream.clean();
			return ;
		}
		
		byte[] send_data = ms.gatherBoardData();
		r = stream.write(send_data);
		if(r == false){
			closeServerFromSocketHandler();
			ms.setStatus("Send Fail.");
			stream.clean();
			return ;
		}
		
		ms.initialDone();
		int rdlen = 0;
		
		while(true)
		{
			this.data.clear();
			rdlen = stream.read();
			if(rdlen != 3){
				System.out.println("?? relen= "+rdlen);
				break;
			}
			this.data.put(stream.getRdbuf(), 0, 3);
			System.out.println("server rd: "+data.array()[0]+" "+data.array()[1]+" "+data.array()[2]);
			r = this.ms.activeTurn(this.data.array());
			if(r){
				System.out.println("normal stop ss");
				break;
			}
			
			this.data.clear();
			this.__wait();
			System.out.println("server wr: "+data.array()[0]+" "+data.array()[1]+" "+data.array()[2]);
			r = stream.write(this.data.array());
			if(r == false){
				break;
			}
		}


		closeServerFromSocketHandler();
	}
	private void __wait(){

		synchronized(ms.getSsLck()){
			while(this.data.position() == 0){
				try {
					ms.getSsLck().wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	void __notify(ByteBuffer bb){
		// set data.
		synchronized(ms.getSsLck()){
			////this.data.put(bb);
			this.data.put(bb.array(), 0 , 3);
			ms.getSsLck().notify();
		}
	}
	///////////////////////////////////////////////
	
	synchronized void closeServer(){
		if(ss != null){
			try {
				ss.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			ss = null;
		}
		if(socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			socket = null;
		}
		if(timer != null){
			timer.cancel();
			timer = null;
		}
	}
	synchronized void closeServerFromSocketHandler(){
		this.sh.stopSs();
	}
}

class __ClientSocket extends Thread
{
	private InetAddress ia;
	private int port;
	private MagicSquare ms;
	private SocketHandler sh;
	private Timer timer;
	private Integer tick;
	private Socket socket;
	private ByteBuffer data;
	//private Object lock;
	static private __JDialog dialog;
	static{
		dialog = null;
	}
	
	private __ClientSocket(InetAddress _ia, int _port, MagicSquare _ms, SocketHandler _sh){
		this.ia = _ia;
		this.port = _port;
		this.ms = _ms;
		this.sh = _sh;
		this.timer = new Timer();
		this.tick = 0;
		this.socket = null;
		this.data = ByteBuffer.allocate(3);
		//this.lock = new Object();
	}
	static __ClientSocket getInstance(InetAddress _ia, int _port, MagicSquare _ms, SocketHandler _sh){
		if(dialog == null){
			dialog = new __JDialog(_ms.getJFrame(), "Connect to Server", "Connect to Server...");
		}
		__ClientSocket ret = new __ClientSocket(_ia, _port, _ms, _sh);
		if(ret.port == -1){
			return null;
		}
		return ret;
	}
	void startClient(){
		timer.schedule(new UpdateSecCounter(dialog, tick), 1000, 1000);
		this.start();
		
		ms.getJDialog().setVisible(false);
		dialog.update(this, "Connect to Server...");
		dialog.setVisible(true);
	}
	public void run()
	{
		try {
			Thread.sleep(1000); // to wait for show dialog...
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		InetSocketAddress isa = new InetSocketAddress(ia, port);
		socket = new Socket();
		try{
			socket.connect(isa, 30000);
		} catch (ConnectException ce) {
			System.out.println("client socket exception: port close.: " + ce.getMessage());
			dialog.setVisible(false);
			closeClientFromSocketHandler();
			ms.setStatus(ce.getMessage());
			return;
		} catch (SocketException se) {
			System.out.println("client socket exception: socket close.: normal shutdown: " + se.getMessage());
			dialog.setVisible(false);
			//closeClientFromSocketHandler(); call by cancel button.
			ms.setStatus(se.getMessage());
			return;
		} catch(SocketTimeoutException ste){
			System.out.println("client socket timeout exception: " + ste.getMessage());
			dialog.setVisible(false);
			closeClientFromSocketHandler();
			ms.setStatus(ste.getMessage());
			return;
		} catch (IOException e) {
			e.printStackTrace();
			dialog.setVisible(false);
			closeClientFromSocketHandler();
			ms.setStatus("Connect Fail");
			return;
		}
		
		timer.cancel();
		timer = null;
		dialog.setVisible(false);
		ms.setStatus("Connect Success");
		
		SocketStream stream = new SocketStream();
		boolean b = stream.newBis(socket);
		if(b == false){
			closeClientFromSocketHandler();
			ms.setStatus("Input Fail");
		}
		b = stream.newBos(socket);
		if(b == false){
			closeClientFromSocketHandler();
			ms.setStatus("Output Fail");
			stream.clean();
		}
		
		int rd_len = stream.read();
		if(rd_len < 0){
			System.out.println("rd_len= "+rd_len);
			closeClientFromSocketHandler();
			ms.setStatus("Read Fail.");
			stream.clean();
		}
		
		byte[] rdbuf = stream.getRdbuf();
		for(int i=0; i<rd_len; i++){
			System.out.print(""+rdbuf[i]+" ");
		}
		
		//// initial client board...
		ms.setBoardData(rdbuf);
		
		ms.initialDone();
		int rdlen = 0;
		
		while(true)
		{
			this.data.clear();
			this.__wait();
			System.out.println("client wr: "+data.array()[0]+" "+data.array()[1]+" "+data.array()[2]);
			b = stream.write(this.data.array());
			if(b == false){
				break;
			}
			
			this.data.clear();
			rdlen = stream.read();
			if(rdlen != 3){
				System.out.println("?? relen= "+rdlen);
				break;
			}
			this.data.put(stream.getRdbuf(), 0, 3);
			System.out.println("client rd: "+data.array()[0]+" "+data.array()[1]+" "+data.array()[2]);
			b = this.ms.activeTurn(this.data.array());
			if(b){
				System.out.println("normal stop cs");
				break;
			}
		}
		
		closeClientFromSocketHandler();
	}
	
	private void __wait(){

		synchronized(ms.getCsLck()){
			while(this.data.position() == 0){
				try {
					ms.getCsLck().wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	void __notify(ByteBuffer bb){
		// set data.
		synchronized(ms.getCsLck()){
			//System.out.println("client notify block.");
			this.data.put(bb.array(), 0 , 3);
			ms.getCsLck().notify();
		}
	}
	
	synchronized void closeClientFromSocketHandler(){
		this.sh.stopCs();
	}
	synchronized void closeClient(){
		if(socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			socket = null;
		}
		if(timer != null){
			timer.cancel();
			timer = null;
		}
	}
	
	/*
	{
		
		static private void my_close(){
			if(s != null){
				try {
					s.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				s = null;
			}
			ia = null;
			port = -1;
			dialog = null;
			isa = null;
			ms = null;
			if(timer != null){
				timer.cancel();
				timer = null;
				client_timetick = 0;
			}
		}



	   client.connect(isa, 10000);
	            BufferedOutputStream out = new BufferedOutputStream(client
	                    .getOutputStream());
	            // 送出字串
	            out.write("Send From Client ".getBytes());
	            out.flush();
	            out.close();
	            out = null;
	            client.close();
	            client = null;

	 */

	
}
class SocketStream
{
	private BufferedInputStream bis;
	private BufferedOutputStream bos;
	private byte[] rdbuf;
	SocketStream(){
		this.bis = null;
		this.bos = null;
		this.rdbuf = new byte[50]; // 50 > max number.
	}
	boolean newBis(Socket s){
		InputStream is = null;
		try {
			is = s.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			is = null;
		}
		if(is == null)
			return false;
		bis = new BufferedInputStream(is);
		return true;
	}
	boolean newBos(Socket s){
		OutputStream os = null;
		try {
			os = s.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
			os = null;
		}
		if(os == null)
			return false;
		bos = new BufferedOutputStream(os);
		return true;
	}
	boolean write(byte[] array){
		try {
			bos.write(array);
			bos.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	int read(){
		int r;
		try {
			r = bis.read(rdbuf);
		} catch (IOException e) {
			e.printStackTrace();
			r = -2;
		}
		return r;
	}
	byte[] getRdbuf(){
		return this.rdbuf;
	}
	void clean(){
		if(bis != null){
			try {
				bis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			bis = null;
		}
		if(bos != null){
			try {
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			bos = null;
		}
	}
}
class UpdateSecCounter extends TimerTask
{
	private __JDialog dialog;
	private Integer tick;
	UpdateSecCounter(__JDialog _d, Integer _tick){
		this.dialog = _d;
		this.tick = _tick;
	}
	public void run() {
		dialog.updatePrint_text(tick);
		tick++;
	}
}

class __JDialog extends JDialog implements ActionListener
{
	private static final long serialVersionUID = 1L;
	private JLabel display;
	private JButton cancel;
	private String print_text;
	private JFrame parent_frame;
	private __ServerSocket ss;
	private __ClientSocket cs;

	__JDialog(JFrame _f, String title, String _print_text)
	{
		super(_f, title, true);
		System.out.println("new __JDialog()...");
		this.parent_frame = _f;
		this.print_text = _print_text;
		this.display = new JLabel(print_text);
		this.cancel = new JButton("cancel");
		this.ss = null;
		this.cs = null;

		setBounds((int)parent_frame.getBounds().getX()+50, (int)parent_frame.getBounds().getY()+50, 250, 150);
		setResizable(false);
		getContentPane().setLayout(new BorderLayout());

		JPanel jp1 = new JPanel();
		jp1.setLayout(new BoxLayout(jp1, BoxLayout.LINE_AXIS));
		jp1.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		jp1.add(Box.createHorizontalGlue());
		jp1.add(this.display);
		jp1.add(Box.createHorizontalGlue());
		getContentPane().add(jp1, BorderLayout.CENTER);

		JPanel jp2 = new JPanel();
		jp2.setLayout(new BoxLayout(jp2, BoxLayout.LINE_AXIS));
		jp2.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		jp2.add(Box.createHorizontalGlue());
		jp2.add(Box.createRigidArea(new Dimension(10, 0)));
		this.cancel.setActionCommand("cancel");
		this.cancel.addActionListener(this);
		jp2.add(this.cancel);
		getContentPane().add(jp2, BorderLayout.SOUTH);

		this.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing(WindowEvent e)
					{
						System.out.println("Closed in __JDialog window adapter.");
						//e.getWindow().dispose();
						cancel();
					}
				}
				);
	}

	/////////////////////////////////////////////////////////

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().equals("cancel")){
			System.out.println("__JDialog, actionPerformed....");
			cancel();
			this.setVisible(false);
		} else {
			System.out.println("why here...");
		}
	}
	
	///////////////////////////////////////////////////////////////////
	
	private void cancel(){
		if(ss != null){
			ss.closeServerFromSocketHandler();
		} else {
			cs.closeClientFromSocketHandler();
		}
	}
	
	void updatePrint_text(int sec){
		this.display.setText(this.print_text + sec +"...");
	}
	private void updatePrint_text(String str){ 
		this.print_text = str;
		this.display.setText(this.print_text);
	}
	void update(__ServerSocket ss, String str){  // always in first time, to call this..
		this.updatePrint_text(str);
		this.ss = ss;
	}
	void update(__ClientSocket cs, String str){  // always in first time, to call this..
		this.updatePrint_text(str);
		this.cs = cs;
	}
}

