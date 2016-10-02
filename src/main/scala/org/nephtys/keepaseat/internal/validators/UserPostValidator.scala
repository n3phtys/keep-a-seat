package org.nephtys.keepaseat.internal.validators

import org.nephtys.keepaseat.internal.posts.UserPost

/**
  * Created by nephtys on 9/28/16.
  */
trait UserPostValidator {

  def validate(userPost : UserPost) : Boolean
}
