package com.smarthome.client2.activity;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.smarthome.client2.R;
import com.smarthome.client2.util.TopBarUtils;
import com.smarthome.client2.view.CustomActionBar;


public class UiEditUserInfoTelphone_sm extends BaseActivity {
	private EditText newPhoneNum;
	private ImageView img_head_ico;
	private TextView tv_head_title;
	private Button head_button;
	private FrameLayout mTitleBar;
	private CustomActionBar mActionBar;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.e_phone_num_sm);
		addTopBarToHead();
		newPhoneNum=(EditText)this.findViewById(R.id.new_phone_number);
	}


	private void addTopBarToHead() {
		mTitleBar = (FrameLayout) findViewById(R.id.edit_phone_number_sm_header);
		if (mActionBar != null) {
			mTitleBar.removeView(mActionBar);
		}
		mActionBar = TopBarUtils.createCustomActionBar(this,
				R.drawable.btn_back_selector,
				new OnClickListener() {

					@Override
					public void onClick(View v) {
						UiEditUserInfoTelphone_sm.this.finish();
					}
				},
				"设置电话号码",
				"提交",
				new OnClickListener() {

					@Override
					public void onClick(View v) {
						String name = newPhoneNum.getText().toString().trim();
						if (newPhoneNum.getText().toString().trim().length() > 11
								|| (newPhoneNum.getText()
								.toString()
								.trim()
								.length() < 11 && newPhoneNum.getText()
								.toString()
								.trim()
								.length() > 0)) {
							Toast.makeText(getApplicationContext(),
									"电话号码无效",
									Toast.LENGTH_SHORT)
									.show();
						}else {
							Bundle param = new Bundle();
							param.putString("newPhoneNum", name);
							UiEditUserInfoTelphone_sm.this.setResult(RESULT_OK, UiEditUserInfoTelphone_sm.this.getIntent().putExtras(param));
							UiEditUserInfoTelphone_sm.this.finish();
						}
					}
				});
		mTitleBar.addView(mActionBar);
	}


}
