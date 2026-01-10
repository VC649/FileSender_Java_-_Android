package com.example.mobileapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.*;
import java.io.*;
import java.util.*;

public class SendFragment extends Fragment {

    private TextView statusText, progressText;
    private ProgressBar progressBar;

    // STORED PC IP (USE THIS LATER)
    private InetAddress javaPcIP;
    private ActivityResultLauncher<Intent> filePickerLauncher, folderPickerLauncher;

    private static final int port = 5000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFilePicker();
        setupFolderPicker();

        if(getArguments() != null) {
            try {
                javaPcIP = InetAddress.getByName(getArguments().getString("PC_IP"));
            } catch(Exception e) {
                javaPcIP = null;
            }
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_send, container, false);

        statusText = view.findViewById(R.id.statusText);
        progressText = view.findViewById(R.id.sendProgressText);
        progressBar = view.findViewById(R.id.progressBarSend);

        Button filesButton = view.findViewById(R.id.filesButton);
        Button folderButton = view.findViewById(R.id.folderButton);

        if(javaPcIP == null) {
            update("No device connection");
            filesButton.setEnabled(false);
        } else {
            update("Connected");
            filesButton.setEnabled(true);
        }

        filesButton.setOnClickListener(v -> openFilePicker());
        folderButton.setOnClickListener(v -> openFolderPicker());

        return view;
    }

    private String getFileName(Uri uri) {
        Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null);

        if(cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

            if(cursor.moveToFirst()) {
                String name = cursor.getString(nameIndex);
                cursor.close();
                return name;
            }
            cursor.close();
        }
        return "unknown";
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                return;
            }
            Intent data = result.getData();

            // MULTIPLE FILES
            if(data.getClipData() != null) {
                ClipData clipData = data.getClipData();

                int count = clipData.getItemCount();
                update("Selected " + count + " files");

                for (int i=0; i < count; i++) {
                    Uri fileUri = clipData.getItemAt(i).getUri();
                    sendFile(fileUri);
                }
            }
            // SINGLE FILE
            else if(data.getData() != null) {
                Uri uri = data.getData();
                update("Selected: " + uri);
                sendFile(uri);
            }
        });
    }

    private void setupFolderPicker() {
        // FOLDER PICKER
        folderPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri treeUri = result.getData().getData();
                handleFolder(treeUri);
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // ANY file
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    private void handleFolder(Uri treeUri) {
        DocumentFile root = DocumentFile.fromTreeUri(requireContext(), treeUri);

        if(root == null || !root.isDirectory()) {
            update("Invalid folder");
            return;
        }

        new Thread(() -> {
            showProgress();

            try(Socket socket = new Socket(javaPcIP, port);
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

                long totalBytes = calculateFolderSize(root);
                long[] sentBytes = {0};

                sendDirectoryRecursive(root, root.getName(),  dos, sentBytes, totalBytes);

                dos.writeUTF("END");
                dos.flush();

                update("Folder sent successfully");
            } catch(Exception e) {
                update("Error: " + e.getMessage());
            } finally {
                hideProgress();
            }
        }).start();
    }

    // ---------- SELECT AND SEND FILES --------------
    private void sendFile(Uri uri) {
        new Thread(() -> {
            showProgress();

            try (Socket socket = new Socket(javaPcIP, port);
                 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))){

                // Send filename
                String fileName = getFileName(uri);
                InputStream is = requireActivity().getContentResolver().openInputStream(uri);

                assert is != null;
                long totalSize = is.available();
                long sentBytes = 0;

                dos.writeUTF("FILE");
                dos.writeUTF(fileName);
                dos.writeLong(totalSize);

                byte[] buffer = new byte[4096];
                int read;

                while((read = is.read(buffer)) != -1) {
                    update("Sending file: " + fileName);
                    dos.write(buffer, 0, read);
                    sentBytes += read;
                    updateProgress(sentBytes, totalSize);
                }
                dos.flush();
                is.close();

                update("Sent: " + fileName);

            } catch(Exception e) {
                update("Error sending file: " + e.getMessage());
            } finally {
                hideProgress();
            }
        }).start();
    }

    private void sendDirectoryRecursive(DocumentFile file, String relativePath, DataOutputStream dos, long[] sentBytes, long totalBytes) throws IOException {
        if(file.isDirectory()) {
            dos.writeUTF("DIR");
            dos.writeUTF(relativePath);
            dos.flush();

            for(DocumentFile child : file.listFiles()) {
                sendDirectoryRecursive(child, relativePath + "/" + child.getName(), dos, sentBytes, totalBytes);
            }
        }
        else if(file.isFile()) {
            dos.writeUTF("FILE");
            dos.writeUTF(relativePath);
            dos.writeLong(file.length());

            try (InputStream is = requireContext().getContentResolver().openInputStream(file.getUri())) {
                byte[] buffer = new byte[4096];
                int read;

                while(true) {
                    update("Sending file: " + file.getName());
                    assert is != null;
                    if ((read = is.read(buffer)) == -1) break;
                    dos.write(buffer, 0, read);

                    // Track progress
                    sentBytes[0] += read;
                    updateProgress(sentBytes[0], totalBytes);
                }
            }
            dos.flush();
        }
    }

    private void showProgress() {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.VISIBLE);

            progressBar.setProgress(0);
            progressText.setText("0%");
        });
    }

    private void updateProgress(long sent, long total) {
        int percent = (int) ((sent * 100) / total);
        requireActivity().runOnUiThread(() -> {
            progressBar.setProgress(percent);
            progressText.setText(percent + "%");
        });
    }

    private void hideProgress() {
        requireActivity().runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            progressText.setVisibility(View.GONE);
        });
    }

    private long calculateFolderSize(DocumentFile file) {
        long size = 0;
        if(file.isDirectory()) {
            for(DocumentFile child : file.listFiles()) {
                size += calculateFolderSize(child);
            }
        } else if(file.isFile()) {
            size += file.length();
        }
        return size;
    }

    private void update(String msg) {
        if(getActivity() != null) {
            getActivity().runOnUiThread(() -> statusText.setText(msg));
        }
    }

}