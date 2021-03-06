package cx.ath.troja.droidippy;

import android.app.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.accounts.*;
import android.net.*;
import android.widget.*;
import android.content.*;
import android.text.*;
import android.text.util.*;
import android.text.method.*;
import android.provider.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import static cx.ath.troja.droidippy.Util.*;

public abstract class BaseActivity extends Activity implements ServiceConnection {

	public static Boolean absoluteTimes = false;
	public static Boolean longGameData = true;


	public interface Cancellable {
		public void cancel();
	}

	public class ToastDisplay implements Cancellable, Runnable {
		public boolean cancelled = false;
		public Toast toast;
		public int duration;
		public ToastDisplay(String s) {
			this.toast = Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT);
			this.duration = s.length() * 80;
		}
		public void cancel() {
			cancelled = true;
			toast.cancel();
		}
		public void run() {
			if (!cancelled) {
				toast.show();
				duration -= 1000;
				if (duration > 0) {
					handler.postDelayed(this, 1000);
				}
			}
		}
	}

	public class OkClickable implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
		}
	}

	protected abstract class HandlerDoable<T> implements Doable<T> {
		public abstract void handle(T t);
		public void doit(final T t) {
			handler.post(new Runnable() {
				public void run() {
					try {			
						handle(t);
					} catch (WindowManager.BadTokenException e) {
						if (!e.getMessage().matches(".*is your activity running.*")) {
							throw e;
						}
					} catch (IllegalArgumentException e) {
						if (!e.getMessage().matches(".*View not attached to window manager.*")) {
							throw e;
						}
					}
				}
			});
		}
	}


	/**
	 * The handler that our new threads use to make us do stuff
	 */
	protected Handler handler;
	protected ProgressDialog progress;
	protected Set<Cancellable> cancellables = new HashSet<Cancellable>();
	protected boolean paused = false;
	protected static boolean premiumService = false;
	protected static float reliability = 0f;

	protected final Doable<RuntimeException> STD_ERROR_HANDLER = new HandlerDoable<RuntimeException>() {
		public void handle(RuntimeException e) {
			hideProgress();
			error(e, true);
		}
	};

	@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
		}

	protected void vibrate(int ms) {
		if (new Settings.System().getInt(this.getContentResolver(),    
					Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
			((Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(ms);
		}
	}

	@Override
		public void onServiceDisconnected(ComponentName name) {
		}

	@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			handler = new Handler();
		}

	@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.help:
					Intent helpIntent = new Intent(getApplicationContext(), Help.class);
					startActivity(helpIntent);
					return true;
				default:
					return super.onOptionsItemSelected(item);
			}
		}

	private void adjustTextViews() {
		if (getFontSizes(this) != 1.0f) {
			adjustTextViews(findViewById(android.R.id.content));
		}
	}

	protected static void adjustTextViews(Context c, Dialog d) {
		if (getFontSizes(c) != 1.0f) {
			adjustTextViews(c, d.findViewById(android.R.id.content));
		}
	}

	protected void adjustTextViews(View v) {
		adjustTextViews(this, v);
	}

	protected static void adjustTextViews(Context c, View v) {
		if (getFontSizes(c) != 1.0f) {
			if (v instanceof TextView) {
				TextView tv = (TextView) v;
				tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * getFontSizes(c));
			}
			if (v instanceof ViewGroup) {
				ViewGroup vg = (ViewGroup) v;
				for (int i = 0; i < vg.getChildCount(); i++) {
					adjustTextViews(c, vg.getChildAt(i));
				}
			}
		}
	}

	@Override
		public void setContentView(int i) {
			super.setContentView(i);
			adjustTextViews();
		}

	@Override
		public void setContentView(View v) {
			super.setContentView(v);
			adjustTextViews();
		}

	@Override
		protected void onResume() {
			super.onResume();
			paused = false;
			longGameData = getLongGameData(this);
			absoluteTimes = getAbsoluteTimes(this);
		}

	@Override
		protected void onPause() {
			super.onPause();
			hideProgress();
			paused = true;
		}

	@Override
		protected void onNewIntent(Intent intent) {
			super.onNewIntent(intent);
			setIntent(intent);
		}

	@Override
		protected void onDestroy() {
			for (Cancellable c : cancellables) {
				c.cancel();
			}
			super.onDestroy();
		}

	protected void register(Cancellable c) {
		cancellables.add(c);
	}

	protected void unregister(Cancellable c) {
		cancellables.remove(c);
	}

	protected void showProgress(int resource) {
		if (progress == null) {
			progress = new ProgressDialog(this);
			progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}
		progress.setMessage(getResources().getString(resource));
		progress.show();
	}

	protected void hideProgress() {
		if (progress != null) {
			progress.cancel();
		}
	}

	protected Cancellable toast(int resource) {
		return toast(getResources().getString(resource));
	}

	protected Cancellable toast(String s) {
		ToastDisplay display = new ToastDisplay(s);
		display.run();
		return display;
	}

	protected void error(RuntimeException e, boolean throwIt) {
		Log.w(getPackageName(), "Got error", e);
		if (e.getCause() instanceof IOException) {
			toast(R.string.error_contacting_server);
		} else if (e.getCause() instanceof android.accounts.OperationCanceledException) {
			toast(R.string.you_must_allow_droidippy_to_authenticate);
		} else if (e instanceof Getter.HttpException) {
			Getter.HttpException h = (Getter.HttpException) e;
			if (h.code == 500) {
				new AlertDialog.Builder(this).setMessage(R.string.server_problem).setTitle(R.string.error).setNeutralButton(R.string.ok, new OkClickable()).show();
			} else if (h.code == 403) {
				new AlertDialog.Builder(this).setMessage(R.string.you_are_banned).setTitle(R.string.banned).setNeutralButton(R.string.ok, new OkClickable()).show();
			} else if (h.code == 406) {
				if (h.body.matches(".*too old.*")) {
					SpannableString message = new SpannableString(getResources().getString(R.string.too_old_client).replace(";", "\n"));
					Linkify.addLinks(message, Linkify.ALL);
					AlertDialog dialog = new AlertDialog.Builder(BaseActivity.this).setMessage(message).setTitle(R.string.bad_client_version).setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse("market://details?id=cx.ath.troja.droidippy"));
							startActivity(intent);
						}
					}).create();
					dialog.show();
					((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
				} else if (h.body.matches(".*too new.*")) {
					new AlertDialog.Builder(BaseActivity.this).setMessage(R.string.too_new_client).setTitle(R.string.bad_client_version).setNeutralButton(R.string.ok, new OkClickable()).show();
				}
			} else {
				new AlertDialog.Builder(this).setMessage(R.string.server_unexpected).setTitle(R.string.error).setNeutralButton(R.string.ok, new OkClickable()).show();
			}
		} else {
			toast(e.getMessage());
			if (throwIt) {
				throw e;
			}
		}
	}

}
