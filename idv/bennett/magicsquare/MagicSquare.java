
package idv.bennett.magicsquare;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Random;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.plaf.metal.MetalLookAndFeel;


/**
 * @author bennett
 * @since 2015/11/30
 */
public class MagicSquare implements ActionListener, KeyListener{

	public enum Direction{ none, up, down, left, right};
	enum GameType{none, single, pair};
	enum GameRole{none, server, client};

	private JFrame frame;
	private JDialog dialog;
	private JPanel ip_panel, size_panel;
	private ButtonGroup bg_server_client, bg_array_size, single_pair;
	private JTextField server_port, server_ip;
	private MyJPanel myboard;
	private StatusBar statusBar;
	private MyJButton spacebtn;
	private SocketHandler sh;
	private boolean isMyTurn;
	private ByteBuffer buf;
	private Object ssLck, csLck;

	MagicSquare(){

		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
			return ;
		}


		frame = new JFrame();
		frame.setBounds(200, 200, 300, 300); //(x,y,width,height)
		frame.setTitle("簡易數字方塊");

		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new BorderLayout());

		myboard = new MyJPanel();
		myboard.setBorder(BorderFactory.createRaisedBevelBorder());
		contentPane.add(myboard, BorderLayout.CENTER);
		////myboard.setFocusable(true);
		////myboard.requestFocusInWindow();

		statusBar = new StatusBar();
		contentPane.add(statusBar, BorderLayout.SOUTH);
		statusBar.setStatus("READY");

		setMenuBar();// set menu bar.

		dialog = initialDialog(); // initial setting dialog.
		sh = new SocketHandler();
		this.isMyTurn = false;
		buf = ByteBuffer.allocate(3);
		ssLck = new Object(); csLck = new Object();

		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);

		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing(WindowEvent e)
					{
						System.out.println("Closed in frame window adapter..");
						//e.getWindow().dispose();
						frame.dispose();
						sh.stopAll();
					}
				}
				);
		frame.addKeyListener(this);

		dialog.setVisible(true);
	}

	private void setMenuBar(){

		JMenuBar menuBar;
		JMenu cmd_menu, help_menu;
		JMenuItem restart_mi, close_mi, rule_mi, about_mi;

		//Create the menu bar.
		menuBar = new JMenuBar();

		//Build the first menu.
		cmd_menu = new JMenu("Commands");
		cmd_menu.setMnemonic(KeyEvent.VK_C);
		menuBar.add(cmd_menu);

		//second menu
		help_menu = new JMenu("Help");
		help_menu.setMnemonic(KeyEvent.VK_H);
		menuBar.add(help_menu);

		restart_mi = new JMenuItem("Restart", KeyEvent.VK_R);
		restart_mi.addActionListener(this);
		cmd_menu.add(restart_mi);
		close_mi = new JMenuItem("Close", KeyEvent.VK_C);
		close_mi.addActionListener(this);
		cmd_menu.add(close_mi);

		rule_mi = new JMenuItem("Rule", KeyEvent.VK_R);
		rule_mi.addActionListener(this);
		help_menu.add(rule_mi);
		about_mi = new JMenuItem("About", KeyEvent.VK_A);
		about_mi.addActionListener(this);
		help_menu.add(about_mi);


		frame.setJMenuBar(menuBar);
	}
	private JDialog initialDialog(){
		JDialog d = new JDialog(frame, "遊戲設定", true);
		d.setBounds((int)frame.getBounds().getX()+50, (int)frame.getBounds().getY()+50, 300, 300);
		d.setResizable(false);
		d.getContentPane().setLayout(new GridLayout(4, 1, 5, 5)); // GridLayout(int rows, int cols, int hgap, int vgap)

		JPanel p1 = new JPanel();
		p1.setLayout(new GridLayout(1, 2, 5, 5));
		p1.setBorder(BorderFactory.createTitledBorder("遊戲模式"));
		p1.setToolTipText("遊戲模式");
		JRadioButton single = new JRadioButton("single_player");
		single.setText("單人模式");
		single.setActionCommand("single_player");
		single.setSelected(true);
		single.addActionListener(this);
		JRadioButton pair = new JRadioButton("pair_player");
		pair.setText("雙人模式");
		pair.setActionCommand("pair_player");
		pair.addActionListener(this);
		single_pair  = new ButtonGroup();
		single_pair.add(single);
		single_pair.add(pair);
		p1.add(single);
		p1.add(pair);
		d.getContentPane().add(p1);

		ip_panel = new JPanel();
		ip_panel.setLayout(new GridLayout(2, 2, 5, 1));
		ip_panel.setBorder(BorderFactory.createTitledBorder("網路設定"));
		ip_panel.setToolTipText("網路設定");
		JRadioButton server_side = new JRadioButton("server_side");
		server_side.setText("等待連線(伺服端)");
		server_side.setActionCommand("server_side");
		server_side.addActionListener(this);
		server_side.setEnabled(false);
		JRadioButton client_side = new JRadioButton("client_side");
		client_side.setText("連到伺服(客戶端)");
		client_side.setActionCommand("client_side");
		client_side.addActionListener(this);
		client_side.setEnabled(false);
		bg_server_client = new ButtonGroup();
		bg_server_client.add(server_side);
		bg_server_client.add(client_side);
		server_port = new JTextField("port number?", 10);
		server_port.setToolTipText("輸入埠號.(1~65536, ex:4400)");
		server_port.setEnabled(false);
		server_ip = new JTextField("server ip?", 15);
		server_ip.setToolTipText("輸入伺服IP:PORT.(ex:192.168.1.2:4400)");
		server_ip.setEnabled(false);
		ip_panel.add(server_side);
		ip_panel.add(client_side);
		ip_panel.add(server_port);
		ip_panel.add(server_ip);
		ip_panel.setEnabled(false);
		d.getContentPane().add(ip_panel);

		size_panel = new JPanel();
		size_panel.setLayout(new GridLayout(2, 2, 1, 1));
		size_panel.setBorder(BorderFactory.createTitledBorder("陣列大小"));
		size_panel.setToolTipText("陣列大小");
		JRadioButton jb3 = new JRadioButton("3*3");
		jb3.setSelected(true);
		////jb3.setActionCommand("3*3");
		jb3.addActionListener(this);
		JRadioButton jb4 = new JRadioButton("4*4");
		jb4.addActionListener(this);
		JRadioButton jb5 = new JRadioButton("5*5");
		jb5.addActionListener(this);
		JRadioButton jb6 = new JRadioButton("6*6");
		jb6.addActionListener(this);
		bg_array_size = new ButtonGroup();
		bg_array_size.add(jb3);
		bg_array_size.add(jb4);
		bg_array_size.add(jb5);
		bg_array_size.add(jb6);
		size_panel.add(jb3);
		size_panel.add(jb4);
		size_panel.add(jb5);
		size_panel.add(jb6);
		d.getContentPane().add(size_panel);

		JPanel jp2 = new JPanel();
		jp2.setLayout(new BoxLayout(jp2, BoxLayout.LINE_AXIS));
		jp2.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		jp2.add(Box.createHorizontalGlue());
		jp2.add(Box.createRigidArea(new Dimension(10, 0)));
		JButton ok = new JButton("確定");
		ok.setToolTipText("確定");
		ok.setActionCommand("ok");
		ok.addActionListener(this);
		jp2.add(ok);
		d.getContentPane().add(jp2);

		return d;
	}

	private GameType getTheGameType(){
		if(single_pair != null){
			if(single_pair.getSelection() != null){
				if(single_pair.getSelection().getActionCommand() != null){
					if(single_pair.getSelection().getActionCommand().equals("single_player")){
						return GameType.single;
					} else if(single_pair.getSelection().getActionCommand().equals("pair_player")){
						return GameType.pair;
					}
				}
			}
		}
		return GameType.none;
	}
	private GameRole getTheGameRole(){
		if(bg_server_client != null){
			if(bg_server_client.getSelection() != null){
				if(bg_server_client.getSelection().getActionCommand() != null){
					if(bg_server_client.getSelection().getActionCommand().equals("server_side")){
						return GameRole.server;
					} else if(bg_server_client.getSelection().getActionCommand().equals("client_side")){
						return GameRole.client;
					}
				}
			}
		}
		return GameRole.none;
	}

	byte[] gatherBoardData(){
		int size = MyJPanel.getMax();
		int len = size * size + 5;
		byte [] ret = new byte[len];
		int r = 0;
		ret[r++] = 'D';
		ret[r++] = 'A';
		ret[r++] = 'T';
		ret[r++] = 'A';
		ret[r++] = (byte)size;
		String[] s = myboard.gatherData();
		for(int i=0; i<s.length; i++){
			if(s[i].equals(" ") == false){
				ret[r++] = (byte)(Integer.parseInt(s[i]));
			} else {
				ret[r++] = (byte)(size*size);
			}
		}
		return ret;
	}
	void setBoardData(byte[] array){
		// check "DATA" first...
		int n = 0;
		if(array[n++] != (byte)'D'){
			System.out.println("...error n+1= "+n); //// TODO: no error handling...
		}
		if(array[n++] != (byte)'A'){
			System.out.println("...error n+1= "+n);
		}
		if(array[n++] != (byte)'T'){
			System.out.println("...error n+1= "+n);
		}
		if(array[n++] != (byte)'A'){
			System.out.println("...error n+1= "+n);
		}
		int size = (int)array[n++];
		String[][] matrix = new String[size][size];
		for(int j=0; j<size; j++){
			for(int i=0; i<size; i++){
				int t = (int)array[n++];
				if(t != size * size){
					matrix[j][i] = ""+t;
				} else {
					matrix[j][i] = " ";
				}
			}
		}
		if(size == 3){
			myboard.setLayout(new GridLayout(3, 3, 1, 1));
			//spacebtn = initial_board(3);
			frame.setSize(160, 200);
		} else if(size == 4){
			myboard.setLayout(new GridLayout(4, 4, 1, 1));
			//spacebtn = initial_board(4);
			frame.setSize(160+50, 200+42);
		} else if(size == 5){
			myboard.setLayout(new GridLayout(5, 5, 1, 1));
			spacebtn = initial_board(5);
			frame.setSize(160+50*2, 200+42*2);
		} else if(size == 6){
			myboard.setLayout(new GridLayout(6, 6, 1, 1));
			spacebtn = initial_board(6);
			frame.setSize(160+5+50*3, 200+42*3);
		}
		SwingUtilities.updateComponentTreeUI(frame);
		
		this.initial_board(size, matrix);
		SwingUtilities.updateComponentTreeUI(frame);
	}
	private void enableAllBtn(boolean b){
		for(int i=0; i<MyJPanel.getMax(); i++){
			for(int j=0; j<MyJPanel.getMax(); j++){
				myboard.getBtn( i, j).setEnabled(b);
			}
		}
		if(b == true){
			spacebtn.setEnabled(false);
		}
	}
	private void setColorAllBtn(Color c){
		for(int i=0; i<MyJPanel.getMax(); i++){
			for(int j=0; j<MyJPanel.getMax(); j++){
				myboard.getBtn( i, j).setBackground(c);
			}
		}
	}
	private MyJButton initial_board(int max, String[][] matrix){ // for pair, client only.
		MyJButton m = null;
		MyJPanel.setMax(max);
		for(int i=0; i<MyJPanel.getMax(); i++){
			for(int j=0; j<MyJPanel.getMax(); j++){
				m = new MyJButton(matrix[i][j], i, j);
				m.setActionCommand(m.getText());
				m.addActionListener(this);
				m.addKeyListener(this);
				myboard.add(m);
				MyJPanel.setMyJButton(i, j, m);
				if(m.getText().equals(" ")){
					m.setEnabled(false);
					m.setBorder(BorderFactory.createLoweredBevelBorder());
					this.spacebtn = m;
				}
			}
		}
		return this.spacebtn;
	}
	private MyJButton initial_board(int max){ // return last MyJButton.
		int now = 1;
		MyJButton m = null;
		MyJPanel.setMax(max);
		for(int i=0; i<MyJPanel.getMax(); i++){
			for(int j=0; j<MyJPanel.getMax(); j++){
				m = new MyJButton(""+now, i, j);
				m.setActionCommand(m.getText());
				m.addActionListener(this);
				m.addKeyListener(this);
				myboard.add(m);
				MyJPanel.setMyJButton(i, j, m);
				now++;
			}
		}
		m.setText(" ");
		m.setEnabled(false);
		m.setBorder(BorderFactory.createLoweredBevelBorder());
		return m;
	}
	private void cleanBoard(){
		Component[] cc = myboard.getComponents();
		for(int i=0; i<cc.length; i++){
			if(cc[i] instanceof MyJButton){
				((MyJButton)cc[i]).removeActionListener(this);
			} else{
				System.out.println("why here?? "+ cc[i].toString());
			}
		}
		myboard.removeAll();
		MyJPanel.setMax(0);
		spacebtn = null;
		statusBar.setSteps(0);
	}
	private void setupMyFrame(){
		Enumeration<AbstractButton> e = bg_array_size.getElements();
		JRadioButton rb = null;
		while(e.hasMoreElements()){
			AbstractButton ab = e.nextElement();
			rb = (JRadioButton)ab;
			//System.out.println("text: "+rb.getText()+" ..."+ rb.isSelected());
			if(rb.isSelected() == true)
				break;
		}

		this.cleanBoard();

		if(rb.getText().equals("3*3")){
			myboard.setLayout(new GridLayout(3, 3, 1, 1));
			spacebtn = initial_board(3);
			frame.setSize(160, 200);
		} else if(rb.getText().equals("4*4")){
			myboard.setLayout(new GridLayout(4, 4, 1, 1));
			spacebtn = initial_board(4);
			frame.setSize(160+50, 200+42);
		} else if(rb.getText().equals("5*5")){
			myboard.setLayout(new GridLayout(5, 5, 1, 1));
			spacebtn = initial_board(5);
			frame.setSize(160+50*2, 200+42*2);
		} else if(rb.getText().equals("6*6")){
			myboard.setLayout(new GridLayout(6, 6, 1, 1));
			spacebtn = initial_board(6);
			frame.setSize(160+5+50*3, 200+42*3);
		}
		SwingUtilities.updateComponentTreeUI(frame);

		Random r = new Random();
		int dd = -1;
		Direction d = null;
		for(int i=0; i<1500; i++){ // move 1500 times.
			dd = r.nextInt(4)+1;
			if(dd == 1){
				d = Direction.up;
			} else if(dd == 2){
				d = Direction.down;
			} else if(dd == 3){
				d = Direction.left;
			} else if(dd == 4){
				d = Direction.right;
			}
			if(myboard.spacedir(spacebtn, d) == false){
				i--;
			} else {
				spacebtn = myboard.changeMyJButton(spacebtn, d);
			}
		}
	}

	private void endProcedure(){
		System.out.println("Success......");
		this.enableAllBtn(false);
		this.setColorAllBtn(Color.BLUE);
		String msg = "Success in " + statusBar.getSteps() + " steps";
		JOptionPane.showMessageDialog(frame, msg);
	}
	// type: keyboard or mouse, btn: button, d: direction.
	private void notifySocket(int type, MyJButton btn, Direction d){
		if(type == 1){
			byte[] ba = buf.array();
			ba[0] = 1;
			String s = btn.getText();
			byte b2 = 0;
			if(s.equals(" ") == true){
				b2 = (byte)(MyJPanel.getMax() * MyJPanel.getMax());
			} else {
				b2 = (byte)(Integer.parseInt(s));
			}
			ba[1] = b2;
			ba[2] = (byte)(d.ordinal());
			if(this.getTheGameRole() == GameRole.client){
				this.sh.notifyCs(buf);
			} else {
				this.sh.notifySs(buf);
			}
		} else if(type == 2){
			byte[] ba = buf.array();
			ba[0] = 2;
			String s = btn.getText();
			byte b2 = 0;
			if(s.equals(" ") == true){
				b2 = (byte)(MyJPanel.getMax() * MyJPanel.getMax());
			} else {
				b2 = (byte)(Integer.parseInt(s));
			}
			ba[1] = b2;
			ba[2] = (byte)(d.ordinal());
			if(this.getTheGameRole() == GameRole.client){
				this.sh.notifyCs(buf);
			} else {
				this.sh.notifySs(buf);
			}
		}
	}
	boolean activeTurn(byte[] array){
		/*for(int i=0; i<array.length; i++){
			System.out.print(" "+array[i]);
		}*/
		if(this.isMyTurn == true){
			System.out.println("why here activeTurn??");
			return false;
		}
		
		int type = (int)array[0];
		if(type == 1){
			int n = (int)array[1];
			String s;
			if( n == MyJPanel.getMax() * MyJPanel.getMax() ){
				s = " ";
			} else {
				s = ""+n;
			}
			MyJButton m = myboard.getBtnByText(s);
			if(m == null){
				// TODO:xx
				return false;
			}
			int di = (int)array[2];
			Direction d = null;
			if(di == 1){
				d = Direction.up;
			} else if(di == 2){
				d = Direction.down;
			} else if(di == 3){
				d = Direction.left;
			} else if(di == 4){
				d = Direction.right;
			}
			if(d == null){
				// TODO:;
			}
			myboard.changeMyJButton( m, d);
			statusBar.incSteps();
			setStatus(" "+statusBar.getSteps()+" steps. Your Turn.");
			this.isMyTurn = true;
			spacebtn = m;
			SwingUtilities.updateComponentTreeUI(myboard);
			if(myboard.isEnd()){
				endProcedure();
				return true;
			}
			return false;
		} else if(type == 2){
			int n = (int)array[1];
			String s;
			if( n == MyJPanel.getMax() * MyJPanel.getMax() ){
				s = " ";
			} else {
				s = ""+n;
			}
			MyJButton m = myboard.getBtnByText(s);
			if(m == null){
				// TODO:xx
				return false;
			}
			int di = (int)array[2];
			Direction d = null;
			if(di == 1){
				d = Direction.up;
			} else if(di == 2){
				d = Direction.down;
			} else if(di == 3){
				d = Direction.left;
			} else if(di == 4){
				d = Direction.right;
			}
			if(d == null){
				// TODO:;
			}
			
			spacebtn = myboard.changeMyJButton(m, d);
			statusBar.incSteps();
			setStatus(" "+statusBar.getSteps()+" steps. Your Turn.");
			this.isMyTurn = true;
			SwingUtilities.updateComponentTreeUI(myboard);
			if(myboard.isEnd()){
				endProcedure();
				return true;
			}
			return false;
		} else {
			// shud not here.
		}
		
		
		
		return false;
	}

	/////////////////////////////////////////////////////////////////////////

	@Override
	public void keyTyped(KeyEvent e) {

	}
	@Override
	public void keyPressed(KeyEvent e) {

	}
	@Override
	public void keyReleased(KeyEvent e) {
		
		int need_send = 0;
		Direction d = null;
		if(this.getTheGameType() == GameType.pair){
			if(this.isMyTurn == false){
				return ;
			} else {
				need_send++;
			}
		}

		switch(e.getKeyCode())
		{
		case KeyEvent.VK_UP:
			System.out.println("up event");
			if(myboard.spacedir(spacebtn, Direction.down) == false){
				;
			} else {
				spacebtn = myboard.changeMyJButton(spacebtn, Direction.down);
				need_send++; d = Direction.down;
			}
			break;
		case KeyEvent.VK_DOWN:
			System.out.println("down event");
			if(myboard.spacedir(spacebtn, Direction.up) == false){
				;
			} else {
				spacebtn = myboard.changeMyJButton(spacebtn, Direction.up);
				need_send++; d = Direction.up;
			}
			break;
		case KeyEvent.VK_LEFT:
			System.out.println("left event");
			if(myboard.spacedir(spacebtn, Direction.right) == false){
				;
			} else {
				spacebtn = myboard.changeMyJButton(spacebtn, Direction.right);
				need_send++; d = Direction.right;
			}
			break;
		case KeyEvent.VK_RIGHT:
			System.out.println("right event");
			if(myboard.spacedir(spacebtn, Direction.left) == false){
				;
			} else {
				spacebtn = myboard.changeMyJButton(spacebtn, Direction.left);
				need_send++; d = Direction.left;
			}
			break;
		}
		
		if(need_send == 2){
			this.isMyTurn = false;
			this.notifySocket(2, spacebtn, d);
		}

		if(e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN
				|| e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT){
			statusBar.incSteps();
			if(need_send == 2){
				setStatus(""+statusBar.getSteps()+" steps. Wait...");
			} else {
				setStatus(" "+statusBar.getSteps()+" steps.");
			}
			SwingUtilities.updateComponentTreeUI(myboard);

			if(myboard.isEnd()){
				endProcedure();
			}
		}

	}

	@Override
	public void actionPerformed(ActionEvent e) {

		System.out.println(e.getActionCommand());

		if(e.getActionCommand().equals("About") == true){
			String msg = " 簡易數字方塊 \n Version: 1.0 \n Author: 陳思凡 (Bennett Chen) \n e-mail: chen.szu.fan@gmail.com";
			JOptionPane.showMessageDialog(frame, msg, "About", JOptionPane.PLAIN_MESSAGE);
		} else if(e.getActionCommand().equals("Rule") == true){
			String rule = "規則：將數字方塊 從左上到右下，由小至大排序 即可完成。";
			JOptionPane.showMessageDialog(frame, rule);
		} else if(e.getActionCommand().equals("Restart") == true){
			statusBar.setStatus("READY.");
			sh.stopAll();
			dialog.setVisible(true);
		} else if(e.getActionCommand().equals("Close") == true){
			////JOptionPane.showMessageDialog(frame, "Close.");
			frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
		} else if(e.getActionCommand().equals("single_player") == true){
			bg_server_client.clearSelection();
			Component[] cs = ip_panel.getComponents();
			for(int i=0; i<cs.length; i++){
				cs[i].setEnabled(false);
			}
			ip_panel.setEnabled(false);
		} else if(e.getActionCommand().equals("pair_player") == true){
			Component[] cs = ip_panel.getComponents();
			for(int i=0; i<cs.length; i++){
				//System.out.println(cs[i].toString());
				if(cs[i] instanceof JRadioButton)
					cs[i].setEnabled(true);
			}
			ip_panel.setEnabled(true);
		} else if(e.getActionCommand().equals("server_side") == true){
			server_ip.setText("server ip?");
			server_ip.setEnabled(false);
			server_port.setEnabled(true);
		} else if(e.getActionCommand().equals("client_side") == true){
			server_port.setText("port number?");
			server_port.setEnabled(false);
			server_ip.setEnabled(true);
		} else if(e.getActionCommand().equals("ok") == true){     //////////// OK button.
			GameType gt = getTheGameType();
			if(gt == GameType.pair){
				System.out.println("pair game start.");
				GameRole gr = getTheGameRole();
				if(gr == GameRole.server){
					System.out.println("server side game");
					String sp_str = server_port.getText();
					int sp_int = -1;
					try {
						sp_int = Integer.parseInt(sp_str);
					} catch(NumberFormatException nfe){
						System.out.println("server port input error? "+nfe.getMessage());
						sp_int = -1;
					}
					if(sp_int == -1){
						JOptionPane.showMessageDialog(dialog, "埠號範圍請用 1~65536.",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					//// initial board.//////
					this.setupMyFrame();
					this.enableAllBtn(false);
					/////////////////////////
					
					boolean b = sh.startSs(sp_int, this);
					if(!b){
						JOptionPane.showMessageDialog(dialog, "請嘗試別的埠號.",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				} else if(gr == GameRole.client){
					System.out.println("client side game");
					String str = server_ip.getText();
					String[] str_array = str.split(":");
					if(str_array.length != 2){
						JOptionPane.showMessageDialog(dialog, "伺服器IP:埠號，如: 127.0.0.1:4400 或 localhost:4401",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					int port = -1;
					try {
						port = Integer.parseInt(str_array[1]);
					} catch(NumberFormatException nfe){
						System.out.println("server port input error? "+nfe.getMessage());
						port = -1;
					}
					if(port == -1){
						JOptionPane.showMessageDialog(dialog, "埠號範圍請用 1~65536.",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					InetAddress ia = null;
					try {
						ia = InetAddress.getByName(str_array[0]);
					} catch(UnknownHostException uhe){
						System.out.println("ip not legal. " + uhe.getMessage());
						ia = null;
					}
					if(ia == null){
						JOptionPane.showMessageDialog(dialog, "伺服器IP:埠號，如: 127.0.0.1:4400 或 localhost:4401",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					//// initial board....////
					this.cleanBoard();
					//////////////////////////
					
					boolean b = sh.startCs(ia, port, this);
					if(!b){
						JOptionPane.showMessageDialog(dialog, "無法連線，請確認有無防火牆。",
								"Input error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				} else {
					JOptionPane.showMessageDialog(dialog, "請設定網路.",
							"Input error", JOptionPane.ERROR_MESSAGE);
				}
			} else if(gt == GameType.single){
				System.out.println("single game start.");
				setupMyFrame();
				dialog.setVisible(false);
				frame.setEnabled(true);
			} else {
				System.out.println("no game type???");
			}
		} else if(e.getActionCommand().equals("3*3") == true){
			//JOptionPane.showMessageDialog(frame, "Close.");
		} else if(e.getActionCommand().equals("4*4") == true){
			//JOptionPane.showMessageDialog(frame, "Close.");
		} else if(e.getActionCommand().equals("5*5") == true){
			//JOptionPane.showMessageDialog(frame, "Close.");
		} else if(e.getActionCommand().equals("6*6") == true){
			//JOptionPane.showMessageDialog(frame, "Close.");
		} else {
			boolean need_send = false;
			if(this.getTheGameType() == GameType.pair){
				if(this.isMyTurn == false){
					return ;
				} else {
					need_send = true;
				}
			}
			
			String ac_str = e.getActionCommand();
			int ac_int = -1;
			try{
				ac_int = Integer.parseInt(ac_str);
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
				ac_int = -1;
			}
			if(ac_int >= 1 && ac_int <= MyJPanel.getMax()*MyJPanel.getMax()-1){
				if(e.getSource() instanceof MyJButton){
					MyJButton m = (MyJButton)e.getSource();
					Direction d = MyJPanel.movedir(m);
					if(d != Direction.none){
						
						if(need_send){
							this.isMyTurn = false;
							this.notifySocket(1, m, d);
						}
						
						myboard.changeMyJButton( m, d);
						statusBar.incSteps();
						if(need_send){
							setStatus(" "+statusBar.getSteps()+" steps. Wait...");
						} else {
							setStatus(" "+statusBar.getSteps()+" steps.");
						}
						spacebtn = m;
						
					} else {

					}

					SwingUtilities.updateComponentTreeUI(myboard);

					//System.out.println("dir= "+d.toString());
					//System.out.println(m.toString());
					//System.out.println(myboard.toString());

					if(myboard.isEnd()){
						endProcedure();
					}

				} else {
					System.out.println("unknown in actionPerformed....xxx");
				}
			}
			else{
				System.out.println("unknown in actionPerformed....");
			}
		}
	}

	///////////////////////////////////////////////////////////////////////

	JFrame getJFrame(){
		return this.frame;
	}
	JDialog getJDialog(){
		return this.dialog;
	}
	void setStatus(String str){
		this.statusBar.setStatus(str);
	}
	void initialDone(){
		GameRole gr = getTheGameRole();
		if(gr == GameRole.server){
			this.isMyTurn = false;
			this.enableAllBtn(true);
			setStatus("Wait...");
		} else {
			this.isMyTurn = true;
			setStatus("Your Turn.");
		}
	}
	Object getSsLck(){
		return this.ssLck;
	}
	Object getCsLck(){
		return this.csLck;
	}
	
	//////////////////////////////////////////////////////////////////

	public static void main(String[] args) 
	{
		Object[] objs = __JOptionPane_PWD.showInputDialog();
		
		if(objs.length != 3){
			return;
		}
		if(Calendar.getInstance().get(Calendar.YEAR) <= 2015){
			// if year = 2015
			if(objs[0] instanceof String){
				String str = (String)objs[0];
				if(str.equals("bennett")){
					new MagicSquare(); // right password.
				}
			}
		} else {
			// compare objs[1], objs[2].
			if(objs[1] instanceof Integer && objs[2] instanceof Integer){
				int t1 = (Integer)objs[1];
				int t2 = (Integer)objs[2];
				if(t1 -1 == t2){
					new MagicSquare(); // right password.
				}
			}
		}
		
	}
}

///////////////////////////////////////////////////////////////////////

class StatusBar extends JPanel
{
	private static final long serialVersionUID = 1L;
	private JLabel statusLabel;
	private int steps;
	StatusBar()
	{
		setLayout(new BorderLayout(2, 2)); 
		statusLabel = new JLabel("Ready"); 
		statusLabel.setBorder(BorderFactory.createLoweredBevelBorder()); 
		statusLabel.setForeground(Color.black);
		add(BorderLayout.CENTER, statusLabel); 
		JLabel dummyLabel = new JLabel(" "); 
		dummyLabel.setBorder(BorderFactory.createLoweredBevelBorder()); 
		add(BorderLayout.EAST, dummyLabel);
	}
	void setStatus(String status) 
	{
		if (status.equals("")) 
			statusLabel.setText("Ready"); 
		else 
			statusLabel.setText(status);
	} 
	String getStatus()
	{
		return statusLabel.getText(); 
	}
	void setSteps(int steps){
		this.steps = steps;
		this.setStatus(" READY.");
	}
	void incSteps(){
		this.steps++;
		//this.setStatus(" "+this.steps+" steps.");
	}
	int getSteps(){
		return this.steps;
	}
}

class MyJButton extends JButton
{
	private static final long serialVersionUID = 1L;
	private int x,y;
	/*sprivate MyJButton(){}
	private MyJButton(String str){}*/
	MyJButton(String text, int y, int x){
		super(text);
		this.x = x;
		this.y = y;
	}
	int get_x(){
		return this.x;
	}
	int get_y(){
		return this.y;
	}
	/*void set_x(int x){
		this.x = x;
	}
	void set_y(int y){
		this.y = y;
	}*/
	boolean isCorrect(){
		int sum = MyJPanel.getMax() * this.get_y() + this.get_x() + 1;
		if(sum != MyJPanel.getMax() * MyJPanel.getMax()){
			if((sum + "").equals(this.getText())){
				return true;
			} else {
				return false;
			}
		} else {
			if(this.getText().equals(" ")){
				return true;
			}
		}
		return false;
	}
	public String toString(){
		return "x="+this.x+" y="+this.y+" text="+getText()+" isCorrect "+this.isCorrect();
	}
}
class MyJPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static int max;
	static private MyJButton[][] myjbs;
	MyJPanel(){
		super();
	}
	static void setMax(int max){
		MyJPanel.max = max;
		if(MyJPanel.max == 0){
			MyJPanel.myjbs = null;
		} else {
			MyJPanel.myjbs = new MyJButton[MyJPanel.max][MyJPanel.max];
		}
	}
	static int getMax(){
		return MyJPanel.max;
	}
	static void setMyJButton(int y, int x, MyJButton m){
		MyJPanel.myjbs[y][x] = m;
	}
	void changeMyJButton(MyJButton a, MyJButton b){
		String aac = a.getActionCommand();
		a.setActionCommand(b.getActionCommand());
		b.setActionCommand(aac);
		String at = a.getText();
		a.setText(b.getText());
		b.setText(at);
		boolean enabled = a.isEnabled();
		a.setEnabled(b.isEnabled());
		b.setEnabled(enabled);
		Border ab = a.getBorder();
		a.setBorder(b.getBorder());
		b.setBorder(ab);
		/*MyJButton tmp = MyJPanel.myjbs[a.get_y()][a.get_x()];
		MyJPanel.myjbs[a.get_y()][a.get_x()] = MyJPanel.myjbs[b.get_y()][b.get_x()];
		MyJPanel.myjbs[b.get_y()][b.get_x()] = tmp;*/
		/*int ax = a.get_x();
		int ay = a.get_y();
		a.set_x(b.get_x());
		a.set_y(b.get_y());
		b.set_x(ax);
		b.set_y(ay);*/
	}
	MyJButton changeMyJButton(MyJButton m, MagicSquare.Direction d){
		MyJButton t = null;
		if(d == MagicSquare.Direction.up){
			t = myjbs[m.get_y()-1][m.get_x()];
		} else if(d == MagicSquare.Direction.down){
			t = myjbs[m.get_y()+1][m.get_x()];
		} else if(d == MagicSquare.Direction.left){
			t = myjbs[m.get_y()][m.get_x()-1];
		} else if(d == MagicSquare.Direction.right){
			t = myjbs[m.get_y()][m.get_x()+1];
		} else {
			// ???
		}
		this.changeMyJButton(m, t);
		return t;
	}
	static MagicSquare.Direction movedir(MyJButton m){
		MyJButton t;
		if(m.get_x() - 1 >= 0){
			t = myjbs[m.get_y()][m.get_x()-1];
			if(t.getText().equals(" ")){
				return MagicSquare.Direction.left;
			}
		}
		if(m.get_x() + 1 < max){
			t = myjbs[m.get_y()][m.get_x()+1];
			if(t.getText().equals(" ")){
				return MagicSquare.Direction.right;
			}
		}
		if(m.get_y() - 1 >= 0){
			t = myjbs[m.get_y()-1][m.get_x()];
			if(t.getText().equals(" ")){
				return MagicSquare.Direction.up;
			}
		}
		if(m.get_y() + 1 < max){
			t = myjbs[m.get_y()+1][m.get_x()];
			if(t.getText().equals(" ")){
				return MagicSquare.Direction.down;
			}
		}
		return MagicSquare.Direction.none;
	}
	boolean spacedir(MyJButton m, MagicSquare.Direction d){
		if(m.getText().equals(" ") == false){
			System.out.println("error....");
			return false;
		}
		if(d == MagicSquare.Direction.left){
			if(m.get_x() - 1 >= 0)
				return true;
		} else if(d == MagicSquare.Direction.right){
			if(m.get_x() + 1 < max)
				return true;
		} else if(d == MagicSquare.Direction.up){
			if(m.get_y() - 1 >= 0)
				return true;
		} else if(d == MagicSquare.Direction.down){
			if(m.get_y() + 1 < max)
				return true;
		}
		return false;
	}
	public String toString(){
		String ret = "";
		for(int i=0; i<max; i++){
			for(int j=0; j<max; j++){
				ret+= "[ "+myjbs[i][j].get_y()+","+myjbs[i][j].get_x()+","+myjbs[i][j].getText()+"],";
			}
			ret += "\n";
		}

		return ret;
	}
	boolean isEnd(){
		for(int j=getMax()-1 ; j>=0; j--){
			for(int i=getMax()-1; i>=0; i--){
				if(myjbs[j][i].isCorrect()==false)
					return false;
			}
		}
		return true;
	}
	String[] gatherData(){
		String[] ret = new String[getMax() * getMax()];
		int r = 0;
		for(int j=0 ; j<getMax(); j++){
			for(int i=0; i<getMax(); i++){
				ret[r] = myjbs[j][i].getText();
				r++;
			}
		}
		return ret;
	}
	MyJButton getBtn(int i, int j){
		return myjbs[i][j];
	}
	MyJButton getBtnByText(String s){
		for(int j=0 ; j<getMax(); j++){
			for(int i=0; i<getMax(); i++){
				if( s.equals(myjbs[j][i].getText()))
					return myjbs[j][i];
			}
		}
		return null;
	}
}

///////////////////////////////////////////////////////////////////////////////////////////

class __JOptionPane_PWD
{
	static Object r[], t1, t2;
	static Object[] showInputDialog(){
		r = new Object[3];
		r[0] = r[1] = r[2] = null;
		t1 = t2 = null;

		JPanel panel = new JPanel();
		JLabel label = new JLabel("Enter a password:");
		JPasswordField pwd = new JPasswordField(10);
		panel.add(label);
		panel.add(pwd);
		String[] options = new String[]{"OK", "Cancel"};
		JOptionPane _optionPane = new JOptionPane(panel, 
				JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[1]);
		JDialog _dialog = _optionPane.createDialog("Password Dialog");

		_dialog.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void keyReleased(KeyEvent e)
					{
						if(t1 == null){
							t1 = e.getKeyCode();System.out.println("t1="+t1);
						} else if(t2 == null){
							t2 = e.getKeyCode();
						}
					}
				}
				);
		_dialog.setFocusable(true);
		_dialog.setVisible(true);
		_dialog.dispose();

		String ok_cancel = (String) _optionPane.getValue();
		if(ok_cancel.equals("OK")){
			String s = new String(pwd.getPassword());
			System.out.println("pw = "+ s);
			r[0] = s;
		} else if(ok_cancel.equals("Cancel")){
			r[1] = t1;
			r[2] = t2;
		} else {
		}
		
		return r;
	}
}






