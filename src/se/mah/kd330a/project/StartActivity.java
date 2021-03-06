package se.mah.kd330a.project;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import se.mah.kd330a.project.adladok.model.Course;
import se.mah.kd330a.project.adladok.model.Me;
import se.mah.kd330a.project.framework.MainActivity;
import se.mah.kd330a.project.home.data.DOMParser;
import se.mah.kd330a.project.home.data.RSSFeed;
import se.mah.kd330a.project.schedule.data.KronoxCalendar;
import se.mah.kd330a.project.schedule.data.KronoxCourse;
import se.mah.kd330a.project.schedule.data.KronoxJSON;
import se.mah.kd330a.project.schedule.data.KronoxReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.EditText;

public class StartActivity extends Activity implements Observer
{
	private final String TAG = "StartActivity";
	private final String RSSNEWSFEEDURL = "http://www.mah.se/Nyheter/RSS/News/";
	private EditText editTextUsername;
	private EditText editTextPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start);
		
		((LinearLayout) findViewById(R.id.login_view)).setVisibility(LinearLayout.GONE);
		((LinearLayout) findViewById(R.id.loading_view)).setVisibility(LinearLayout.GONE);

		
		Me.observable.deleteObservers();
		Me.observable.addObserver(this);
		Me.restoreMe(getApplicationContext());

		if (Me.getFirstName().isEmpty())
		{
			showLoginView();
		}
		else
		{
			hideLoginView();
			Me.updateMe();
		}

	}

	public void showLoginView()
	{
		/*
		 * Hide the other view
		 */
		((View) findViewById(R.id.loading_view)).setVisibility(View.GONE);
		
		((View) findViewById(R.id.login_view)).setVisibility(View.VISIBLE);
		editTextUsername = (EditText) findViewById(R.id.editText1);
		editTextPassword = (EditText) findViewById(R.id.editText2);
		editTextUsername.setText(Me.getUserID());
		editTextPassword.setText("");
	}

	public void hideLoginView()
	
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(
			      Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow((IBinder) findViewById(R.id.login_view).getWindowToken(), 0);
		
		((View) findViewById(R.id.login_view)).setVisibility(View.GONE);
		((View) findViewById(R.id.loading_view)).setVisibility(View.VISIBLE);
	}

	public void forgetButtonClicked(View v)
	{
		Me.setUserID("");
		Me.setPassword("");

		Toast.makeText(this, "You've been forgotten.", Toast.LENGTH_SHORT).show();
		finish();
	}

	public void loginButtonClicked(View v)
	{
		hideLoginView();
		
		String username = editTextUsername.getText().toString();
		String password = editTextPassword.getText().toString();

		/* 
		 * Reset the Me "object"
		 */
		Me.setDispayName("");
		Me.setEmail("");
		Me.setFirstName("");
		Me.setIsStudent(false);
		Me.setIsStaff(false);
		Me.setLastName("");
		Me.clearCourses();
		Me.setUserID(username);
		Me.setPassword(password);
		Me.updateMe();
	}

	/*
	 * Called by "Me" after login button is clicked 
	 */
	@Override
	public void update(Observable observable, Object data)
	{
		Log.i(TAG, "update(): Got callback from Me");

		if (Me.getFirstName().isEmpty())
		{
			showLoginView();
			Toast.makeText(this, "Unable to log you in", Toast.LENGTH_LONG).show();
			return;
		}

		Me.saveMe(getApplicationContext());
		BackgroundDownloadTask downloads = new BackgroundDownloadTask(this);
		downloads.execute();
	}

	/**
	 * When all tasks have completed we can go on to the next activity
	 */
	public void tasksCompleted()
	{
		Intent intent = new Intent(this, MainActivity.class);
		startActivity(intent);
		finish();
	}

	private class BackgroundDownloadTask extends AsyncTask<Void, Void, Void>
	{
		private StartActivity appContext;

		public BackgroundDownloadTask(StartActivity activity)
		{
			appContext = activity;
		}

		@Override
		protected Void doInBackground(Void... arg0)
		{
			try
			{
				Log.i(TAG, "RSS: Save a rss feed for god knows what reason");
				DOMParser myParser = new DOMParser();
				RSSFeed feed = myParser.parseXml(RSSNEWSFEEDURL);
				FileOutputStream fout = openFileOutput("filename", Context.MODE_PRIVATE);
				ObjectOutputStream out = new ObjectOutputStream(fout);
				out.writeObject(feed);
				out.close();
				fout.close();
			}
			catch (Exception e)
			{
				Log.e(TAG, e.toString());
			}

			if (!Me.getCourses().isEmpty())
			{
				Log.i(TAG, "Kronox: setting up courses");
				ArrayList<KronoxCourse> courses = new ArrayList<KronoxCourse>();

				for (Course c : Me.getCourses())
				{
					String courseId = c.getKronoxCalendarCode().substring(2);
					courses.add(new KronoxCourse(courseId));
				}

				KronoxCourse[] courses_array = new KronoxCourse[courses.size()];
				courses.toArray(courses_array);

				try
				{
					Log.i(TAG, "Kronox: something");
					KronoxCourse course = KronoxJSON.getCourse(courses_array[0].getFullCode());
					if (course != null)
					{
						SharedPreferences sp = getSharedPreferences("courseName", Context.MODE_PRIVATE);
						SharedPreferences.Editor editor = sp.edit();
						editor.putString(course.getFullCode(), course.getName());
						editor.commit();
						Log.i(TAG, String.format("Course: %s, %s", course.getFullCode(), course.getName()));

						try
						{
							Log.i(TAG, "Kronox: Creating calendar");
							KronoxCalendar.createCalendar(KronoxReader.getFile(getApplicationContext()));
						}
						catch (Exception e)
						{
							Log.i(TAG, "Kronox: Downloading schedule, then creating calendar");
							try
							{
								KronoxReader.update(getApplicationContext(), courses_array);
								KronoxCalendar.createCalendar(KronoxReader.getFile(getApplicationContext()));
							}
							catch (Exception f)
							{
								Log.e(TAG, f.toString());
							}
						}
					}
				}
				catch (Exception e)
				{
					Log.e(TAG, e.toString());
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void v)
		{
			appContext.tasksCompleted();
		}

	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		/*
		 *  Make sure we're not registered observers anymore, otherwise
		 *  more and more instances will be created each time we start 
		 *  the app
		 */
		Me.observable.deleteObserver(this);

		Log.i(TAG, "finish(): destroying now");

	}
	
	
}
