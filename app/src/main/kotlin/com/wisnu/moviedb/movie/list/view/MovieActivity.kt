package com.wisnu.moviedb.movie.list.view

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.jakewharton.rxbinding2.support.v4.widget.RxSwipeRefreshLayout
import com.wisnu.moviedb.R
import com.wisnu.moviedb.costumview.SortingDialogFragment
import com.wisnu.moviedb.di.MovieApplication
import com.wisnu.moviedb.movie.detail.view.ItemOffsetDecoration
import com.wisnu.moviedb.movie.detail.view.MovieDetailActivity
import com.wisnu.moviedb.movie.list.model.MovieDiscover
import com.wisnu.moviedb.movie.list.model.MovieSorting
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_movie_list.*

class MovieActivity : AppCompatActivity() {

    private val TAG = MovieActivity::class.java.simpleName

    private val preference by lazy { MovieApplication.movieComponent.providePreferenceManager() }
    private val movieListPresenter by lazy { MovieApplication.movieComponent.provideMovieListPresenter() }
    private val endlessScrollListener: EndlessScrollListener by lazy {
        EndlessScrollListener(
            movies_grid.layoutManager as GridLayoutManager, { onLoadMore() }
        )
    }

    private var recentSorting = MovieSorting.BY_POPULARITY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_list)

        initToolbar()
        initMoviesLayoutManager()
        initSwipeRefresh()
        showMoviesBySorting(MovieSorting.BY_POPULARITY)
        updateMovieBySorting()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = getString(R.string.main_title)
    }

    private fun initSwipeRefresh() {
        Observable.just(true)
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { RxSwipeRefreshLayout.refreshes(swipe_to_refresh) }
            .map { refreshMovie() }
            .subscribe(
                {
                    Log.d(TAG, "doOnNext initSwipeRefresh")
                    hideLoading()
                },
                { Log.d(TAG, "onError refreshMovie") },
                { Log.d(TAG, "onComplete initSwipeRefresh") }
            )
    }

    private fun initMoviesLayoutManager() {
        movies_grid.addItemDecoration(ItemOffsetDecoration(resources.getDimensionPixelOffset(R.dimen.movie_item_offset)))
        movies_grid.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.movies_columns))
        movies_grid.addOnScrollListener(endlessScrollListener)
    }

    private fun onLoadMore() {
        Observable.just(true)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { showLoading() }
            .observeOn(Schedulers.io())
            .flatMap {
                movieListPresenter.retrieveDiscoverMovies(recentSorting, getMovieAdapter().movieList)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorResumeNext(Function { Observable.just(null) })
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(TAG, "doOnNext onLoadMore")

                    getMovieAdapter().addMovieList(it.results, movies_grid)
                    hideLoading()
                },
                {
                    Log.d(TAG, "onError onLoadMore")
                    hideLoading()
                },
                { Log.d(TAG, "onComplete onLoadMore") }
            )
    }

    private fun showLoading() {
        endlessScrollListener.loading = true
        swipe_to_refresh.isRefreshing = true
    }

    private fun hideLoading() {
        endlessScrollListener.loading = false
        swipe_to_refresh.isRefreshing = false
    }

    private fun showMovie() {
        view_no_movies.visibility = View.GONE
        movies_grid.visibility = View.VISIBLE
    }

    private fun showNoMovie() {
        view_no_movies.visibility = View.VISIBLE
        movies_grid.visibility = View.GONE
    }

    private fun getMovieAdapter(): MoviesAdapter {
        return movies_grid.adapter as MoviesAdapter
    }

    private fun onItemMovieClicked(movieDiscover: MovieDiscover) {
        val intent = Intent(this, MovieDetailActivity::class.java)
        intent.putExtra(MovieDetailActivity.MOVIE_ID, movieDiscover)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.sort_menu_fragment, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_show_sort_by_dialog -> showSortDialog()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showMoviesBySorting(sortState: String) {
        Observable.just(true)
            .observeOn(Schedulers.io())
            .flatMap {
                movieListPresenter.retrieveDiscoverMovies(sortState)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorResumeNext(Function { Observable.just(null) })
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(TAG, "doOnNext showMoviesBySorting")

                    movies_grid.adapter = MoviesAdapter(it.results, { onItemMovieClicked(it) })
                    showMovie()
                },
                {
                    Log.d(TAG, "onError showMoviesBySorting")
                    showNoMovie()
                },
                { Log.d(TAG, "onComplete showMoviesBySorting") }
            )
    }

    private fun refreshMovie() {
        Observable.just(true)
            .observeOn(Schedulers.io())
            .flatMap {
                movieListPresenter.retrieveDiscoverMovies(recentSorting)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorResumeNext(Function { Observable.just(null) })
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(TAG, "doOnNext refreshMovie")

                    movies_grid.adapter = MoviesAdapter(it.results, { onItemMovieClicked(it) })
                    showMovie()
                },
                {
                    Log.d(TAG, "onError refreshMovie")
                    showNoMovie()
                },
                { Log.d(TAG, "onComplete refreshMovie") }
            )
    }

    private fun showSortDialog() {
        val sortingDialogFragment: DialogFragment = SortingDialogFragment()
        sortingDialogFragment.show(supportFragmentManager, SortingDialogFragment.TAG)
    }

    private fun updateMovieBySorting() {
        Observable
            .just(true)
            .flatMap { movieListPresenter.listenSortingEvent() }
            .map { showMoviesBySorting(preference.getSortState()) }
            .subscribe { recentSorting = preference.getSortState() }
    }

}
