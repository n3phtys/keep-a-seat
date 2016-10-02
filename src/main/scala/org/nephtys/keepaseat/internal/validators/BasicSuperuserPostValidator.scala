package org.nephtys.keepaseat.internal.validators
import org.nephtys.keepaseat.internal.posts.SuperuserPost

/**
  * Created by nephtys on 10/2/16.
  */
class BasicSuperuserPostValidator() extends SuperuserPostValidator{
  override def validate(post: SuperuserPost): Boolean = {
    //only allow delete if no confirm in json, also: non of that delete = false bullshit
    (post.delete.isDefined && post.delete.get && post.confirm.isEmpty) || (post.confirm.isDefined && post.delete.isEmpty)
  }
}
