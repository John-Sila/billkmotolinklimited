package com.example.billkmotolinkltd.ui.mpesa

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.databinding.FragmentMpesaBinding
import com.example.billkmotolinkltd.ui.ChatActivity
import com.example.billkmotolinkltd.ui.MpesaActivity

class MpesaFragment : Fragment() {

    private var _binding: FragmentMpesaBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMpesaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createMpesaRoomButton.setOnClickListener {
            val intent = Intent(context, MpesaActivity::class.java)
            context?.startActivity(intent)
        }

    }
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
