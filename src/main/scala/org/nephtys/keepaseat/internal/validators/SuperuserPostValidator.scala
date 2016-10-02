package org.nephtys.keepaseat.internal.validators

import org.nephtys.keepaseat.internal.posts.SuperuserPost

/**
  * Created by nephtys on 9/28/16.
  */
trait SuperuserPostValidator {
  def validate(post: SuperuserPost): Boolean

}
