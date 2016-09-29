package org.nephtys.keepaseat.internal.configs

/**
  * If this is thrown, the route definitions have to be loaded completely anew. This probably means shutting down the
  * existing
  * ActorSystem and recreating it, followed by rebinding the HTTP and rebuilding the routes
  * Created by nephtys on 9/28/16.
  */
class CriticalConfigChangedException(msg : String) extends Exception(msg) {

}
