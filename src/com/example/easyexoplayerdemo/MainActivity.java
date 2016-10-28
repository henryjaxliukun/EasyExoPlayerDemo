package com.example.easyexoplayerdemo;

import java.io.File;

import com.example.easyexoplayerdemo.exoplayer.ExtractorPlayerActivity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	TextView tv;
	EditText et;
	Button btn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initWidgets();
	}

	private void initWidgets() {
		tv = (TextView) findViewById(R.id.tv_status);
		et = (EditText) findViewById(R.id.et_file_uri);
		et.setText("test.mp4");
		btn = (Button) findViewById(R.id.btn_play);

		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String contentUri = et.getText().toString();
				String defaultUri="file://" +Environment.getExternalStorageDirectory() + File.separator;
				if(checkIsFile(contentUri)){
					contentUri=defaultUri+contentUri;
					playUri("file://"+contentUri);
				}else{
					showToast("file not exist:"+contentUri);
				}
			}
		});
	}

	/**
	 * play uri as file
	 * @param uri
	 */
	private void playUri(String uri) {
		Intent mpdIntent = new Intent(this, ExtractorPlayerActivity.class).setData(Uri.parse(uri));
		startActivity(mpdIntent);
	}

	/**
	 * check uri is a file
	 */
	private boolean checkIsFile(String uri) {
		File file=new File(Environment.getExternalStorageDirectory() + File.separator+uri);
		if(file.exists()){
			return true;
		}else{
			return false;
		}
	}

	private void showToast(String text) {
		Log.i("test", text);
		Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
	}
}
