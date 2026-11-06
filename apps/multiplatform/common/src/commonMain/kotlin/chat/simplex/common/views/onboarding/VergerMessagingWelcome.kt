package chat.simplex.common.views.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.helpers.LocalAppBarHandler
import chat.simplex.common.views.helpers.ModalView
import chat.simplex.common.views.helpers.rememberAppBarHandler

/** Verger-only entry to the encrypted messaging engine.
 * Upstream onboarding remains available outside Verger, notably for migrations.
 * Legal and open-source attributions are preserved in the distributed project.
 */
@Composable
fun VergerMessagingWelcome(onCreateProfile: () -> Unit) {
  CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
    ModalView({}, showClose = false, showAppBar = false) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(32.dp))
        Text("🌱", fontSize = 54.sp)
        Spacer(Modifier.height(16.dp))
        Text(
          "Verger Associations",
          style = MaterialTheme.typography.h1,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Text(
          "La messagerie de votre association",
          style = MaterialTheme.typography.h2,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Text(
          "Échangez dans le groupe privé de votre association. Vos messages sont chiffrés de bout en bout.",
          style = MaterialTheme.typography.body1,
          textAlign = TextAlign.Center,
          lineHeight = 24.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
          "Créez un profil de messagerie sur cet appareil, puis rejoignez le groupe grâce à une invitation des responsables. Aucun numéro de téléphone n’est nécessaire.",
          style = MaterialTheme.typography.body2,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
          lineHeight = 22.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
          onClick = onCreateProfile,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
          shape = RoundedCornerShape(26.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Text("Créer mon profil de messagerie", fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Text(
          "Le profil de messagerie est distinct de votre compte d’administration Verger.",
          style = MaterialTheme.typography.caption,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
