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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
 * 短信登录窗口。验证码和登录请求均在后台执行。
 */
public class DialogSMSLogin extends JDialog {

	private static final long serialVersionUID = 3741671572332334944L;
	private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+(\\d{1,3}) +(\\d+)$");

	public static volatile DialogSMSLogin Instance;

	private final INeedLogin inl;
	private final JTextField phoneField = new JTextField();
	private final JTextField smsCodeField = new JTextField();
	private final JButton captchaButton = new MJButton("获取短信验证码");
	private final JButton loginButton = new MJButton("登录");
	private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
	private final AtomicBoolean cleanupStarted = new AtomicBoolean();

	private volatile boolean refreshingCaptcha;
	private volatile boolean sendingSms;
	private volatile boolean loggingIn;
	private volatile boolean closing;
	private volatile SocketServer socketServer;
	private volatile String countryCode;
	private volatile String phoneNumber;
	private volatile String captchaKey;
	private volatile String pendingCaptchaToken;

	public DialogSMSLogin(INeedLogin inl) {
		this.inl = inl;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new DialogSMSLogin(new INeedLogin()).init();
			}
		});
	}

	public void init() {
		Instance = this;
		startCallbackServer();
		setTitle("BilibiliDown - 短信登录");
		setModal(true);
		setAlwaysOnTop(true);
		setResizable(true);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setContentPane(createContentPanel());
		getRootPane().setDefaultButton(loginButton);
		pack();
		setMinimumSize(new Dimension(480, 350));
		setLocationRelativeTo(null);
		if (Global.userName != null && Global.userName.trim().startsWith("+")) {
			phoneField.setText(Global.userName);
		}
		setVisible(true);
	}

	private JPanel createContentPanel() {
		JPanel root = new JPanel(new BorderLayout(0, 16));
		root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(205, 210, 216)),
				new EmptyBorder(16, 24, 20, 24)));
		root.setBackground(Color.WHITE);

		ImageIcon banner = new ImageIcon(DialogSMSLogin.class.getResource("/resources/banner.jpg"));
		root.add(new JLabel(banner, SwingConstants.CENTER), BorderLayout.NORTH);

		JPanel form = new JPanel(new GridBagLayout());
		form.setOpaque(false);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(5, 5, 5, 5);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 0.0;
		form.add(new JLabel("手机号"), constraints);
		constraints.gridx = 1;
		constraints.weightx = 1.0;
		phoneField.setToolTipText("格式示例：+86 18812344321");
		form.add(phoneField, constraints);

		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.weightx = 0.0;
		form.add(new JLabel("短信验证码"), constraints);
		constraints.gridx = 1;
		constraints.weightx = 1.0;
		form.add(smsCodeField, constraints);

		JPanel actions = new JPanel(new GridBagLayout());
		actions.setOpaque(false);
		GridBagConstraints actionConstraints = new GridBagConstraints();
		actionConstraints.gridx = 0;
		actionConstraints.weightx = 1.0;
		actionConstraints.fill = GridBagConstraints.HORIZONTAL;
		actionConstraints.insets = new Insets(0, 0, 0, 5);
		captchaButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				queryCaptchaInBackground();
			}
		});
		actions.add(captchaButton, actionConstraints);
		actionConstraints.gridx = 1;
		actionConstraints.insets = new Insets(0, 5, 0, 0);
		loginButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				loginInBackground();
			}
		});
		actions.add(loginButton, actionConstraints);

		constraints.gridx = 0;
		constraints.gridy = 2;
		constraints.gridwidth = 2;
		constraints.weightx = 1.0;
		form.add(actions, constraints);

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
		}, "Thread-SmsLoginServer");
		serverThread.setDaemon(true);
		serverThread.start();
	}

	private void queryCaptchaInBackground() {
		if (refreshingCaptcha || sendingSms || loggingIn) {
			return;
		}
		Matcher matcher = PHONE_PATTERN.matcher(phoneField.getText() == null ? "" : phoneField.getText().trim());
		if (!matcher.matches()) {
			showStatus("手机号格式应为：+国际区号 手机号码", true);
			return;
		}
		countryCode = matcher.group(1);
		phoneNumber = matcher.group(2);
		Global.userName = "+" + countryCode + " " + phoneNumber;
		captchaKey = null;
		pendingCaptchaToken = null;
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
					if (closing) {
						return;
					}
					pendingCaptchaToken = token;
					String url = String.format(
							"http://localhost:%d/static/index.html?token=%s&gt=%s&challenge=%s&type=sms",
							Global.serverPort, token, gt, challenge);
					openCaptchaUrl(url);
					showStatus("请在浏览器完成验证码", false);
				} catch (Exception error) {
					pendingCaptchaToken = null;
					showStatus("验证码获取失败，请检查网络或端口占用", true);
				} finally {
					refreshingCaptcha = false;
					setControlsBusy(false, null);
				}
			}
		}, "Thread-SmsCaptcha");
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
				JOptionPane.showMessageDialog(DialogSMSLogin.this,
						"当前系统无法自动打开浏览器。验证码地址已复制到剪贴板。", "请注意",
						JOptionPane.WARNING_MESSAGE);
			}
		});
	}

	/** 由本地验证码回调线程调用。 */
	public synchronized String sendSMS(String token, String challenge, String validate, String seccode) {
		if (closing) {
			return "登录窗口已关闭";
		}
		if (sendingSms || pendingCaptchaToken == null || !pendingCaptchaToken.equals(token)) {
			showStatus("验证码已失效，请重新获取", true);
			return "验证码已失效，请重新获取";
		}
		sendingSms = true;
		setControlsBusy(true, "正在发送短信验证码...");
		try {
			JSONObject response = inl.sendSMS(countryCode, phoneNumber, token, challenge, validate, seccode);
			if (closing) {
				return "登录窗口已关闭";
			}
			if (response == null || response.optJSONObject("data") == null) {
				throw new IllegalStateException("短信接口未返回有效数据");
			}
			captchaKey = response.getJSONObject("data").getString("captcha_key");
			pendingCaptchaToken = null;
			showStatus("短信验证码已经发送", false);
			return null;
		} catch (Exception error) {
			captchaKey = null;
			showStatus("短信验证码发送失败，请重新尝试", true);
			return "短信验证码发送失败，请重新尝试";
		} finally {
			sendingSms = false;
			setControlsBusy(false, null);
		}
	}

	private void loginInBackground() {
		if (refreshingCaptcha || sendingSms || loggingIn) {
			return;
		}
		final String code = smsCodeField.getText() == null ? "" : smsCodeField.getText().trim();
		if (captchaKey == null) {
			showStatus("请先获取短信验证码", true);
			return;
		}
		if (code.isEmpty()) {
			showStatus("短信验证码不能为空", true);
			return;
		}
		loggingIn = true;
		setControlsBusy(true, "正在登录...");
		final String loginCountryCode = countryCode;
		final String loginPhoneNumber = phoneNumber;
		final String loginCaptchaKey = captchaKey;
		Thread loginThread = new Thread(new Runnable() {
			@Override
			public void run() {
				String result;
				try {
					result = inl.loginSMS(loginCountryCode, loginPhoneNumber, code, loginCaptchaKey);
				} catch (RuntimeException error) {
					result = "登录失败，请检查网络后重试";
				} finally {
					loggingIn = false;
				}
				if (closing) {
					return;
				}
				final String loginResult = result;
				SwingDispatch.runLater(new Runnable() {
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
			}
		}, "Thread-SmsLogin");
		loginThread.setDaemon(true);
		loginThread.start();
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
				captchaButton.setEnabled(!busy);
				loginButton.setEnabled(!busy);
				phoneField.setEnabled(!busy);
				smsCodeField.setEnabled(!busy);
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

	private void cleanup() {
		if (!cleanupStarted.compareAndSet(false, true)) {
			return;
		}
		closing = true;
		pendingCaptchaToken = null;
		captchaKey = null;
		smsCodeField.setText("");
		Instance = null;
		final SocketServer server = socketServer;
		if (server != null) {
			Thread shutdownThread = new Thread(new Runnable() {
				@Override
				public void run() {
					server.stopServer();
				}
			}, "Thread-SmsLoginServerShutdown");
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
