package com.example.billkmotolinkltd.ui.dailyreports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.billkmotolinkltd.databinding.FragmentDailyreportsBinding

class DailyreportsFragment : Fragment() {

    private var _binding: FragmentDailyreportsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dailyreportsViewModel =
            ViewModelProvider(this).get(DailyreportsViewModel::class.java)

        _binding = FragmentDailyreportsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDailyreports
        dailyreportsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}