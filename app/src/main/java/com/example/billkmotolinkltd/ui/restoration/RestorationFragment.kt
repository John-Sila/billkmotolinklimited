package com.example.billkmotolinkltd.ui.restoration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.billkmotolinkltd.databinding.FragmentRestorationBinding

class RestorationFragment: Fragment() {
    private var _binding: FragmentRestorationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestorationBinding.inflate(inflater, container, false)
        return binding.root
    }
}