package com.example.billkmotolinkltd.ui.logout

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.MainActivity
import com.example.billkmotolinkltd.R
import com.google.firebase.auth.FirebaseAuth
import com.example.billkmotolinkltd.databinding.FragmentLogoutBinding

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    private var _binding: FragmentLogoutBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLogoutBinding.bind(view)

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            // Restart MainActivity to go back to LoginFragment
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
