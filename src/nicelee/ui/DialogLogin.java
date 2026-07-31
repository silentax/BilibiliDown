package nicelee.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.json.JSONObject;

import nicelee.bilibili.INeedLogin;
import nicelee.server.core.SocketServer;
import nicelee.ui.item.JOptionPane;
import nicelee.ui.item.MJButton;
import nicelee.ui.util.SwingDispatch;

/**
 * 密码登录窗口。网络请求在后台执行，Swing 状态只在 EDT 更新。
 */
public class DialogLogin extends JDialog {

	private static final long serialVersionUID = 3741671572332334943L;
	private static final String USER_PLACEHOLDER = "请输入手机号或邮箱";

	public static volatile DialogLogin Instance;

	private final INeedLogin inl;
	private final JTextField userNameField = new JTextField();
	private final JPasswordField passwordField = new JPasswordField();
	private final JButton loginButton = new MJButton("获取验证码并登录");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
	private final Object credentialLock = new Object();
	private final AtomicLong captchaRequestSequence = new AtomicLong();
	private final AtomicBoolean cleanupStarted = new AtomicBoolean();

	private volatile boolean refreshingCaptcha;
	private volatile boolean loggingIn;
	private volatile boolean closing;
	private volatile SocketServer socketServer;
	private char[] pendingPassword;
	private String pendingCaptchaToken;
	private long pendingRequestId;

	public DialogLogin(INeedLogin inl) {
		this.inl = inl;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new DialogLogin(new INeedLogin()).init();
			}
		});
	}

	public void init() {
		Instance = this;
		startCallbackServer();
		setTitle("BilibiliDown - 密码登录");
		setModal(true);
		setAlwaysOnTop(true);
		setResizable(true);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setContentPane(createContentPanel());
		getRootPane().setDefaultButton(loginButton);
		pack();
		setMinimumSize(new Dimension(480, 330));
		setLocationRelativeTo(null);
		if (Global.userName != null && !Global.userName.trim().isEmpty()) {
			userNameField.setText(Global.userName);
		}
		passwordField.setText("");
		setVisible(true);
	}

	private JPanel createContentPanel() {
		JPanel root = new JPanel(new BorderLayout(0, 16));
		root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(205, 210, 216)),
				new EmptyBorder(16, 24, 20, 24)));
		root.setBackground(Color.WHITE);

		ImageIcon banner = new ImageIcon(DialogLogin.class.getResource("/resources/banner.jpg"));
		root.add(new JLabel(banner, SwingConstants.CENTER), BorderLayout.NORTH);

		JPanel form = new JPanel(new GridBagLayout());
		form.setOpaque(false);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(5, 5, 5, 5);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 0.0;
		form.add(new JLabel("账号"), constraints);
		constraints.gridx = 1;
		constraints.weightx = 1.0;
		userNameField.setToolTipText(USER_PLACEHOLDER);
		form.add(userNameField, constraints);

		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.weightx = 0.0;
		form.add(new JLabel("密码"), constraints);
		constraints.gridx = 1;
		constraints.weightx = 1.0;
		passwordField.setToolTipText("密码仅在本次验证码登录期间保留，不写入配置文件");
		form.add(passwordField, constraints);

		constraints.gridx = 0;
		constraints.gridy = 2;
		constraints.gridwidth = 2;
		constraints.weightx = 1.0;
		loginButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				queryCaptchaInBackground();
			}
		});
		form.add(loginButton, constraints);

		constraints.gridy = 3;
		statusLabel.setForeground(new Color(80, 80, 80));
		form.add(statusLabel, constraints);
		root.add(form, BorderLayout.CENTER);
		return root;
	}

	private void startCallbackServer() {
		Thread serverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				SocketServer server = new SocketServer(Global.serverPort);
				socketServer = server;
				if (!closing) {
					server.startServer();
				}
			}
		}, "Thread-PasswordLoginServer");
		serverThread.setDaemon(true);
		serverThread.start();
	}

	private void queryCaptchaInBackground() {
		if (refreshingCaptcha || loggingIn) {
			return;
		}
		String userName = userNameField.getText() == null ? "" : userNameField.getText().trim();
		char[] password = passwordField.getPassword();
		if (userName.isEmpty() || password.length == 0) {
			Arrays.fill(password, '\0');
			showStatus("账号和密码不能为空", true);
			return;
		}

		Global.userName = userName;
		passwordField.setText("");
		final long requestId = captchaRequestSequence.incrementAndGet();
		replacePendingPassword(requestId, password);
		refreshingCaptcha = true;
		setControlsBusy(true, "正在获取验证码...");

		Thread captchaThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					JSONObject geetest = inl.getGeetest();
					String token = geetest.getString("token");
					String gt = geetest.getJSONObject("geetest").getString("gt");
					String challenge = geetest.getJSONObject("geetest").getString("challenge");
					if (!markCaptchaReady(requestId, token)) {
						return;
					}
					if (closing) {
						clearPendingPassword(requestId);
						return;
					}
					String url = String.format("http://localhost:%d/static/index.html?token=%s&gt=%s&challenge=%s",
							Global.serverPort, token, gt, challenge);
					openCaptchaUrl(url);
					showStatus("请在浏览器完成验证码", false);
				} catch (Exception error) {
					clearPendingPassword(requestId);
					showStatus("验证码获取失败，请检查网络或端口占用", true);
				} finally {
					refreshingCaptcha = false;
					setControlsBusy(false, null);
				}
			}
		}, "Thread-PasswordCaptcha");
		captchaThread.setDaemon(true);
		captchaThread.start();
	}

	private void openCaptchaUrl(final String url) throws Exception {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			Desktop.getDesktop().browse(new URI(url));
			return;
		}
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(new StringSelection(url), null);
		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				JOptionPane.showMessageDialog(DialogLogin.this,
						"当前系统无法自动打开浏览器。验证码地址已复制到剪贴板。", "请注意",
						JOptionPane.WARNING_MESSAGE);
			}
		});
	}

	/** 由本地验证码回调线程调用。 */
	public synchronized String login(String token, String challenge, String validate, String seccode) {
		if (loggingIn) {
			return "登录请求正在处理中";
		}
		char[] password = takePendingPassword(token);
		if (password == null) {
			showStatus("验证码已失效，请重新输入密码并获取验证码", true);
			return "验证码已失效，请重新获取";
		}

		loggingIn = true;
		setControlsBusy(true, "正在登录...");
		String passwordText = new String(password);
		String result;
		try {
			result = inl.login(Global.userName, passwordText, token, challenge, validate, seccode);
			if (closing) {
				result = "登录窗口已关闭";
			}
		} catch (RuntimeException error) {
			result = "登录失败，请检查网络后重试";
		} finally {
			Arrays.fill(password, '\0');
			passwordText = null;
			loggingIn = false;
		}
		final String loginResult = result;
		SwingDispatch.runAndWait(new Runnable() {
			@Override
			public void run() {
				if (closing) {
					return;
				}
				if (loginResult == null) {
					showStatus("登录成功", false);
					Global.isLogin = true;
					dispose();
				} else {
					showStatus(loginResult, true);
					setControlsBusy(false, null);
				}
			}
		});
		return result;
	}

	private void setControlsBusy(final boolean busy, final String status) {
		if (closing) {
			return;
		}
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				if (closing) {
					return;
				}
				loginButton.setEnabled(!busy);
				userNameField.setEnabled(!busy);
				passwordField.setEnabled(!busy);
				if (status != null) {
					showStatus(status, false);
				}
			}
		});
	}

	private void showStatus(final String message, final boolean error) {
		if (closing) {
			return;
		}
		SwingDispatch.runLater(new Runnable() {
			@Override
			public void run() {
				if (closing) {
					return;
				}
				statusLabel.setForeground(error ? new Color(170, 45, 45) : new Color(45, 105, 65));
				statusLabel.setText(message);
			}
		});
	}

	private void replacePendingPassword(long requestId, char[] password) {
		synchronized (credentialLock) {
			clearPendingPasswordLocked();
			pendingRequestId = requestId;
			pendingPassword = password;
			pendingCaptchaToken = null;
		}
	}

	private boolean markCaptchaReady(long requestId, String token) {
		synchronized (credentialLock) {
			if (closing || pendingPassword == null || pendingRequestId != requestId) {
				return false;
			}
			pendingCaptchaToken = token;
			return true;
		}
	}

	private char[] takePendingPassword(String token) {
		synchronized (credentialLock) {
			if (pendingPassword == null || pendingCaptchaToken == null || !pendingCaptchaToken.equals(token)) {
				return null;
			}
			char[] password = pendingPassword;
			pendingPassword = null;
			pendingCaptchaToken = null;
			return password;
		}
	}

	private void clearPendingPassword(long requestId) {
		synchronized (credentialLock) {
			if (pendingRequestId == requestId) {
				clearPendingPasswordLocked();
			}
		}
	}

	private void clearPendingPasswordLocked() {
		if (pendingPassword != null) {
			Arrays.fill(pendingPassword, '\0');
		}
		pendingPassword = null;
		pendingCaptchaToken = null;
	}

	private void cleanup() {
		if (!cleanupStarted.compareAndSet(false, true)) {
			return;
		}
		closing = true;
		captchaRequestSequence.incrementAndGet();
		synchronized (credentialLock) {
			clearPendingPasswordLocked();
		}
		passwordField.setText("");
		Instance = null;
		final SocketServer server = socketServer;
		if (server != null) {
			Thread shutdownThread = new Thread(new Runnable() {
				@Override
				public void run() {
					server.stopServer();
				}
			}, "Thread-PasswordLoginServerShutdown");
			shutdownThread.setDaemon(true);
			shutdownThread.start();
		}
	}

	@Override
	public void dispose() {
		cleanup();
		super.dispose();
	}
}
