package org.maproulette.filters

import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter

/**
  * @author cuthbertm
  */
class Filters @Inject() (corsFilter: CORSFilter) extends DefaultHttpFilters(corsFilter)
