package org.maproulette.filters

import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

/**
  * @author cuthbertm
  */
class Filters @Inject() (corsFilter: CORSFilter, gzipFilter: GzipFilter)
  extends DefaultHttpFilters(corsFilter, gzipFilter)
