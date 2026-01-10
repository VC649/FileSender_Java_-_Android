package com.example.mobileapp;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.net.InetAddress;

public class HomeFragment extends Fragment {

    private TextView statusText;
    private DiscoveryManager discoveryManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        statusText = view.findViewById(R.id.showText);

        Button sendButton = view.findViewById(R.id.sendButton);

        discoveryManager = new DiscoveryManager();

        sendButton.setOnClickListener(v -> discoveryManager.startDiscovery(new DiscoveryManager.Callback() {
            @Override
            public void onConnected(InetAddress pcIp) {
                requireActivity().runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putString("PC_IP", pcIp.getHostAddress());

                    SendFragment fragment = new SendFragment();
                    fragment.setArguments(args);

                    requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
                });
            }

            @Override
            public void onStatus(String msg) {
                requireActivity().runOnUiThread(() -> statusText.setText(msg));
            }
        }));

        Button receiveButton = view.findViewById(R.id.receiveButton);
        receiveButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new ReceiveFragment()).commit());

        return view;
    }
}