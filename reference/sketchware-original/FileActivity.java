package Manish.vault;

import Manish.vault.databinding.*;
import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.appbar.AppBarLayout;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public class FileActivity extends AppCompatActivity {
	
	private FileBinding binding;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		binding = FileBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		initialize(_savedInstanceState);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		final android.widget.LinearLayout fileContainer =
		(android.widget.LinearLayout) findViewById(R.id.file_container);
		
		if (fileContainer == null) {
			return;
		}
		
		// Purani generated rows hatao
		for (int i = fileContainer.getChildCount() - 1; i >= 0; i--) {
			android.view.View v = fileContainer.getChildAt(i);
			
			if (v.getId() != R.id.file_title &&
			v.getId() != R.id.add_file_btn) {
				
				fileContainer.removeViewAt(i);
			}
		}
		
		// VaultFiles folder
		final java.io.File vaultFiles =
		new java.io.File(
		getFilesDir(),
		"VaultFiles"
		);
		
		if (!vaultFiles.exists()) {
			vaultFiles.mkdirs();
		}
		
		java.io.File[] files =
		vaultFiles.listFiles();
		
		if (files == null || files.length == 0) {
			
			android.widget.TextView empty =
			new android.widget.TextView(
			FileActivity.this
			);
			
			empty.setText("📂 Abhi koi file nahi hai");
			empty.setTextSize(16);
			empty.setTextColor(
			android.graphics.Color.WHITE
			);
			empty.setPadding(12, 25, 12, 25);
			
			fileContainer.addView(empty);
			
			return;
		}
		
		// Files list
		for (final java.io.File file : files) {
			
			if (!file.isFile()) {
				continue;
			}
			
			final android.widget.LinearLayout row =
			new android.widget.LinearLayout(
			FileActivity.this
			);
			
			row.setOrientation(
			android.widget.LinearLayout.HORIZONTAL
			);
			
			row.setGravity(
			android.view.Gravity.CENTER_VERTICAL
			);
			
			row.setPadding(12, 12, 12, 12);
			
			// Icon
			final android.widget.TextView icon =
			new android.widget.TextView(
			FileActivity.this
			);
			
			String name =
			file.getName().toLowerCase();
			
			if (name.endsWith(".pdf")) {
				
				icon.setText("📄");
				
			} else if (
			name.endsWith(".zip") ||
			name.endsWith(".rar")
			) {
				
				icon.setText("📦");
				
			} else if (
			name.endsWith(".doc") ||
			name.endsWith(".docx")
			) {
				
				icon.setText("📝");
				
			} else if (
			name.endsWith(".xls") ||
			name.endsWith(".xlsx")
			) {
				
				icon.setText("📊");
				
			} else if (
			name.endsWith(".ppt") ||
			name.endsWith(".pptx")
			) {
				
				icon.setText("📽");
				
			} else if (
			name.endsWith(".txt") ||
			name.endsWith(".csv")
			) {
				
				icon.setText("📃");
				
			} else {
				
				icon.setText("📁");
			}
			
			icon.setTextSize(32);
			
			row.addView(
			icon,
			new android.widget.LinearLayout.LayoutParams(
			70,
			70
			)
			);
			
			// File name
			final android.widget.TextView fileName =
			new android.widget.TextView(
			FileActivity.this
			);
			
			fileName.setText(
			file.getName()
			);
			
			fileName.setTextSize(16);
			
			fileName.setTextColor(
			android.graphics.Color.WHITE
			);
			
			fileName.setPadding(
			15,
			0,
			5,
			0
			);
			
			row.addView(
			fileName,
			new android.widget.LinearLayout.LayoutParams(
			0,
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
			1
			)
			);	// FILE CLICK
			row.setOnClickListener(
			new android.view.View.OnClickListener() {
				@Override
				public void onClick(android.view.View v) {
					
					try {
						
						String lowerName =
						file.getName().toLowerCase();
						
						String mimeType = "*/*";
						
						if (lowerName.endsWith(".pdf")) {
							mimeType = "application/pdf";
						} else if (lowerName.endsWith(".zip")) {
							mimeType = "application/zip";
						} else if (lowerName.endsWith(".rar")) {
							mimeType = "application/x-rar-compressed";
						} else if (lowerName.endsWith(".txt")) {
							mimeType = "text/plain";
						} else if (lowerName.endsWith(".csv")) {
							mimeType = "text/csv";
						} else if (lowerName.endsWith(".doc")) {
							mimeType = "application/msword";
						} else if (lowerName.endsWith(".docx")) {
							mimeType =
							"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
						} else if (lowerName.endsWith(".xls")) {
							mimeType = "application/vnd.ms-excel";
						} else if (lowerName.endsWith(".xlsx")) {
							mimeType =
							"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
						} else if (lowerName.endsWith(".ppt")) {
							mimeType = "application/vnd.ms-powerpoint";
						} else if (lowerName.endsWith(".pptx")) {
							mimeType =
							"application/vnd.openxmlformats-officedocument.presentationml.presentation";
						}
						
						android.net.Uri uri =
						androidx.core.content.FileProvider.getUriForFile(
						FileActivity.this,
						getPackageName() + ".fileprovider",
						file
						);
						
						android.content.Intent intent =
						new android.content.Intent(
						android.content.Intent.ACTION_VIEW
						);
						
						intent.setDataAndType(
						uri,
						mimeType
						);
						
						intent.addFlags(
						android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
						);
						
						startActivity(intent);
						
					} catch (Exception e) {
						
						SketchwareUtil.showMessage(
						getApplicationContext(),
						"File open nahi ho saki ❌"
						);
					}
				}
			}
			);
			
			// Row ko container me add karo
			fileContainer.addView(
			row,
			new android.widget.LinearLayout.LayoutParams(
			android.view.ViewGroup.LayoutParams.MATCH_PARENT,
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT
			)
			);
		}
		setSupportActionBar(binding.Toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		binding.Toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _v) {
				onBackPressed();
			}
		});
		
		binding.fileContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				
			}
		});
		
		binding.fileTitle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				
			}
		});
		
		binding.addFileBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SketchwareUtil.showMessage(
				getApplicationContext(),
				"ADD FILE CLICK"
				);
			}
		});
	}
	
	private void initializeLogic() {
	}
	
	@Override
	protected void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_requestCode == 1001 &&
		_resultCode == RESULT_OK &&
		_data != null) {
			
			android.net.Uri selectedUri =
			_data.getData();
			
			if (selectedUri == null) {
				SketchwareUtil.showMessage(
				getApplicationContext(),
				"File select nahi hui ❌"
				);
				return;
			}
			
			try {
				
				java.io.File vaultFiles =
				new java.io.File(
				getFilesDir(),
				"VaultFiles"
				);
				
				if (!vaultFiles.exists()) {
					vaultFiles.mkdirs();
				}
				
				String fileName = null;
				
				android.database.Cursor cursor =
				getContentResolver().query(
				selectedUri,
				null,
				null,
				null,
				null
				);
				
				if (cursor != null) {
					
					int nameIndex =
					cursor.getColumnIndex(
					android.provider.OpenableColumns.DISPLAY_NAME
					);
					
					if (nameIndex >= 0 &&
					cursor.moveToFirst()) {
						
						fileName =
						cursor.getString(nameIndex);
					}
					
					cursor.close();
				}
				
				if (fileName == null ||
				fileName.trim().isEmpty()) {
					
					fileName =
					"VaultFile_" +
					System.currentTimeMillis();
				}
				
				java.io.File destination =
				new java.io.File(
				vaultFiles,
				fileName
				);
				
				if (destination.exists()) {
					
					destination =
					new java.io.File(
					vaultFiles,
					System.currentTimeMillis()
					+ "_"
					+ fileName
					);
				}
				
				java.io.InputStream inputStream =
				getContentResolver()
				.openInputStream(selectedUri);
				
				if (inputStream == null) {
					return;
				}
				
				java.io.FileOutputStream outputStream =
				new java.io.FileOutputStream(
				destination
				);
				
				byte[] buffer =
				new byte[8192];
				
				int length;
				
				while (
				(length =
				inputStream.read(buffer)) > 0
				) {
					
					outputStream.write(
					buffer,
					0,
					length
					);
				}
				
				outputStream.flush();
				outputStream.close();
				inputStream.close();
				
				SketchwareUtil.showMessage(
				getApplicationContext(),
				"File Vault me save ho gayi ✅"
				);
				
				recreate();
				
			} catch (Exception e) {
				
				SketchwareUtil.showMessage(
				getApplicationContext(),
				"File save error ❌"
				);
			}
		}
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
}