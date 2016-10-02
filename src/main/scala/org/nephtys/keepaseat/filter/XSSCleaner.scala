package org.nephtys.keepaseat.filter



import java.io.InputStream

import org.owasp.html.Sanitizers

/**
  * Created by nephtys on 10/2/16.
  */
class XSSCleaner() {

      /**
        * just throw everything away that smells like HTML / Javascript. We only want text
        * formatting seems smallest prepackaged sanitizer
        */
      val policy = Sanitizers.FORMATTING

      /**
        * unencodes the @ sign, because alone it isn't dangerous, but makes the usage simpler on the client side
        * @param untrustedHTML
        * @return
        */
      def removeHTML(untrustedHTML : String) : String = policy.sanitize(untrustedHTML).replace("""&#64;""", """@""")
}