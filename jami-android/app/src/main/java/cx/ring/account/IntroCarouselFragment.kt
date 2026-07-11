/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package cx.ring.account

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import cx.ring.R
import cx.ring.databinding.FragIntroCarouselBinding
import cx.ring.databinding.ItemIntroSlideBinding

/**
 * A short, skippable illustrated introduction shown once before the account
 * creation screen on first launch. It explains, in plain language, what Jami is
 * so a brand-new user knows what to expect before creating an account.
 */
class IntroCarouselFragment : Fragment() {

    private data class Slide(val imageRes: Int, val titleRes: Int, val descriptionRes: Int)

    private val slides = listOf(
        Slide(R.drawable.intro_illustration_connect, R.string.intro_slide1_title, R.string.intro_slide1_description),
        Slide(R.drawable.intro_illustration_private, R.string.intro_slide2_title, R.string.intro_slide2_description),
        Slide(R.drawable.intro_illustration_free, R.string.intro_slide3_title, R.string.intro_slide3_description),
    )

    private var binding: FragIntroCarouselBinding? = null
    private val dotViews = mutableListOf<ImageView>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragIntroCarouselBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        b.introPager.adapter = SlideAdapter()

        buildDots()
        updateDots(0)
        updateNextButton(0)

        b.introPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateNextButton(position)
            }
        })

        b.introSkip.setOnClickListener { finishIntro() }
        b.introNext.setOnClickListener {
            val current = b.introPager.currentItem
            if (current < slides.size - 1) b.introPager.currentItem = current + 1
            else finishIntro()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dotViews.clear()
        binding = null
    }

    private fun buildDots() {
        val b = binding ?: return
        b.introDots.removeAllViews()
        dotViews.clear()
        val margin = dpToPx(4)
        val sizePx = dpToPx(10)
        slides.indices.forEach { _ ->
            val dot = ImageView(requireContext())
            dot.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginStart = margin
                marginEnd = margin
            }
            b.introDots.addView(dot)
            dotViews.add(dot)
        }
    }

    private fun updateDots(selected: Int) {
        dotViews.forEachIndexed { index, dot ->
            dot.setImageResource(
                if (index == selected) R.drawable.intro_dot_active else R.drawable.intro_dot_inactive
            )
        }
    }

    private fun updateNextButton(position: Int) {
        binding?.introNext?.setText(
            if (position == slides.size - 1) R.string.intro_get_started else R.string.intro_next
        )
    }

    private fun finishIntro() {
        requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_HAS_SEEN_INTRO, true)
            .apply()
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.wizard_container, HomeAccountCreationFragment(), HomeAccountCreationFragment.TAG)
            .commit()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private inner class SlideAdapter : RecyclerView.Adapter<SlideAdapter.SlideViewHolder>() {
        inner class SlideViewHolder(val itemBinding: ItemIntroSlideBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder =
            SlideViewHolder(ItemIntroSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = slides.size

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val slide = slides[position]
            holder.itemBinding.introImage.setImageResource(slide.imageRes)
            holder.itemBinding.introTitle.setText(slide.titleRes)
            holder.itemBinding.introDescription.setText(slide.descriptionRes)
        }
    }

    companion object {
        val TAG = IntroCarouselFragment::class.simpleName!!
        const val PREFS_NAME = "wizard_prefs"
        const val PREF_HAS_SEEN_INTRO = "has_seen_intro"
    }
}
