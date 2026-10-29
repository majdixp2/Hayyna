package com.example.ui.components

import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * صفحة التاجر:
 * - الدخول بحساب Google فقط (حساب حقيقي مربوط بـ Firebase)
 * - التاجر يشوف عروضه هو فقط، مع إحصائيات حقيقية لكل عرض
 */
@Composable
fun MerchantPortalView(
    isLoggedIn: Boolean,
    merchantEmail: String?,
    merchantRestaurantName: String,
    merchantOffers: List<RestaurantOffer>,
    isSigningIn: Boolean,
    nowMillis: Long,
    onGoogleSignIn: () -> Unit,
    onLogout: () -> Unit,
    onOpenAddOfferSheet: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    onEditOffer: (RestaurantOffer) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    branchesCount: Int = 0,
    onManageBranches: () -> Unit = {},
    isSuspended: Boolean = false,
    isTrusted: Boolean = true,
    // حسابي: عميل أو تاجر
    isMerchantAccount: Boolean = true,
    isRoleLoading: Boolean = false,
    onCustomerSignIn: () -> Unit = {},
    onBecomeMerchant: () -> Unit = {},
    favoritesCount: Int = 0,
    onShowFavorites: () -> Unit = {},
    onOpenContact: () -> Unit = {},
    unreadReplies: Int = 0,
    verifyEnabled: Boolean = false,
    isVerified: Boolean = false,
    verifyStatus: String? = null,
    onOpenVerify: () -> Unit = {},
    // الاسم والجوال
    userName: String = "",
    userPhone: String = "",
    currentRole: String = com.example.ui.MerchantViewModel.ROLE_CUSTOMER,
    onSaveProfile: (name: String, phone: String, role: String) -> Unit = { _, _, _ -> },
    roleResolved: Boolean = true,
    credits: MerchantCredits? = null,
    catalogCard: (@Composable () -> Unit)? = null,
    onMyOrders: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var editProfile by remember { mutableStateOf(false) }
    if (editProfile) {
        ProfileEditDialog(
            initialName = userName, initialPhone = userPhone, initialRole = currentRole, email = merchantEmail.orEmpty(),
            onSave = onSaveProfile, onDismiss = { editProfile = false }
        )
    }
    val subtitle = listOf(userName, toLocalPhone(userPhone)).filter { it.isNotBlank() }.joinToString(" • ")
    if (isLoggedIn && !isRoleLoading && roleResolved && (userName.isBlank() || userPhone.isBlank())) {
        ProfileSetupView(
            initialName = userName,
            initialPhone = userPhone,
            initialRole = currentRole,
            isSaving = false,
            onSave = onSaveProfile,
            onLogout = onLogout,
            modifier = modifier
        )
    } else if (!isLoggedIn) {
        MerchantLoginView(
            isSigningIn = isSigningIn,
            onGoogleSignIn = onGoogleSignIn,
            onCustomerSignIn = onCustomerSignIn,
            onOpenContact = onOpenContact,
            modifier = modifier
        )
    } else if (isRoleLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CalmSagePrimary)
        }
    } else if (!isMerchantAccount) {
        CustomerAccountView(
            email = toLocalPhone(userPhone),
            name = userName,
            onEditProfile = { editProfile = true },
            favoritesCount = favoritesCount,
            onShowFavorites = onShowFavorites,
            onBecomeMerchant = onBecomeMerchant,
            onLogout = onLogout,
            onDeleteAccount = onDeleteAccount,
            onOpenContact = onOpenContact,
            unreadReplies = unreadReplies,
            onMyOrders = onMyOrders,
            modifier = modifier
        )
    } else {
        MerchantDashboard(
            merchantEmail = subtitle,
            onEditProfile = { editProfile = true },
            merchantName = merchantRestaurantName,
            offers = merchantOffers,
            nowMillis = nowMillis,
            onOpenAddOfferSheet = onOpenAddOfferSheet,
            onLogout = onLogout,
            onOfferClick = onOfferClick,
            onDeleteOffer = onDeleteOffer,
            onEditOffer = onEditOffer,
            onDeleteAccount = onDeleteAccount,
            branchesCount = branchesCount,
            onManageBranches = onManageBranches,
            isSuspended = isSuspended,
            isTrusted = isTrusted,
            onOpenContact = onOpenContact,
            unreadReplies = unreadReplies,
            verifyEnabled = verifyEnabled,
            isVerified = isVerified,
            verifyStatus = verifyStatus,
            onOpenVerify = onOpenVerify,
            credits = credits,
            catalogCard = catalogCard,
            modifier = modifier
        )
    }
}

@Composable
private fun MerchantLoginView(
    isSigningIn: Boolean,
    onGoogleSignIn: () -> Unit,
    onCustomerSignIn: () -> Unit,
    onOpenContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("merchant_login_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "حسابي",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "اختر نوع حسابك — الدخول بحساب Google، مجاناً وبدون كلمة مرور",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        AccountChoiceCard(
            emoji = "🙋",
            title = "دخول كعميل",
            subtitle = "احفظ عروضك المفضلة وتابع متاجر حيّك",
            buttonText = "الدخول كعميل",
            filled = false,
            isSigningIn = isSigningIn,
            onClick = onCustomerSignIn,
            testTag = "customer_google_sign_in_btn"
        )
        AccountChoiceCard(
            emoji = "🏪",
            title = "عندك متجر أو محل؟",
            subtitle = "سجّل متجرك، وانشر عروضك والمنيو، وخلّ أهل الحي يوصلون لك من الخريطة",
            buttonText = "تسجيل التاجر",
            filled = true,
            isSigningIn = isSigningIn,
            onClick = onGoogleSignIn,
            testTag = "merchant_google_sign_in_btn"
        )

        Text(
            text = "تقدر تتصفح العروض والخريطة بدون تسجيل دخول",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        ContactUsButton(unread = 0, onClick = onOpenContact)
    }
}

/** زر "تواصل معنا" مع علامة للردود الجديدة. */
@Composable
private fun ContactUsButton(unread: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📨", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("تواصل معنا", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("مشكلة، اقتراح، أو فكرة؟ نسمعك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (unread > 0) {
                Surface(shape = CircleShape, color = Color(0xFFE05A47)) {
                    Text(
                        "$unread رد جديد",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text("›", fontSize = 22.sp, color = CalmSagePrimary)
            }
        }
    }
}

@Composable
private fun AccountChoiceCard(
    emoji: String,
    title: String,
    subtitle: String,
    buttonText: String,
    filled: Boolean,
    isSigningIn: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("$emoji  $title", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val content: @Composable () -> Unit = {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        color = if (filled) Color.White else CalmSagePrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (filled) {
                Button(
                    onClick = onClick,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag(testTag)
                ) { content() }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag(testTag)
                ) { content() }
            }
        }
    }
}

/** حساب العميل: المفضلة، وتحويل الحساب لتاجر، والخروج. */
@Composable
private fun CustomerAccountView(
    email: String,
    name: String,
    onEditProfile: () -> Unit,
    favoritesCount: Int,
    onShowFavorites: () -> Unit,
    onBecomeMerchant: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenContact: () -> Unit,
    unreadReplies: Int,
    onMyOrders: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPrivacy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmMerchant by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = CalmSagePrimary.copy(alpha = 0.12f), modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("🙋", fontSize = 24.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "أهلاً بك" }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("🛍️ حساب زبون", fontSize = 11.sp, color = CalmSagePrimary, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.IconButton(onClick = onEditProfile) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل بياناتي", tint = CalmSagePrimary)
                }
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("خروج", fontSize = 12.sp) }
            }
        }

        Card(
            onClick = onShowFavorites,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❤️", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("المفضلة ($favoritesCount)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("اعرض عروضك المحفوظة على الخريطة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 22.sp, color = CalmSagePrimary)
            }
        }

        if (onMyOrders != null) Card(
            onClick = onMyOrders,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🧾", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("طلباتي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("طلباتك من المحلات وحالتها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 22.sp, color = CalmSagePrimary)
            }
        }

        // صاحب محل؟ يسجّل خروج ويدخل بحساب صاحب محل (ما نحوّل حساب أهل الحي من هنا)
        Text(
            "🏪 صاحب محل؟ سجّل خروج وادخل باختيار «صاحب محل» عشان تسجّل متجرك.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )

        ContactUsButton(unread = unreadReplies, onClick = onOpenContact)

        AccountSettingsSection(onPrivacy = { showPrivacy = true }, onDelete = { confirmDelete = true })
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (confirmMerchant) {
        AlertDialog(
            onDismissRequest = { confirmMerchant = false },
            title = { Text("تحويل الحساب لتاجر؟") },
            text = { Text("بتصير عندك لوحة التاجر لنشر العروض وصفحة متجرك. عروضك الأولى تتم مراجعتها قبل الظهور.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMerchant = false
                    onBecomeMerchant()
                }) { Text("نعم، سجّل متجري", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmMerchant = false }) { Text("إلغاء") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الحساب نهائياً؟") },
            text = { Text("بيتم حذف حسابك نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) { Text("احذف نهائياً", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun MerchantDashboard(
    merchantEmail: String,
    onEditProfile: () -> Unit,
    merchantName: String,
    offers: List<RestaurantOffer>,
    nowMillis: Long,
    onOpenAddOfferSheet: () -> Unit,
    onLogout: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    onEditOffer: (RestaurantOffer) -> Unit,
    onDeleteAccount: () -> Unit,
    branchesCount: Int,
    onManageBranches: () -> Unit,
    isSuspended: Boolean,
    isTrusted: Boolean,
    onOpenContact: () -> Unit,
    unreadReplies: Int,
    verifyEnabled: Boolean,
    isVerified: Boolean,
    verifyStatus: String?,
    onOpenVerify: () -> Unit,
    credits: MerchantCredits? = null,
    catalogCard: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPrivacy by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var offerPendingDelete by remember { mutableStateOf<RestaurantOffer?>(null) }
    val activeCount = offers.count { !it.draft && !OfferTime.isExpired(it, nowMillis) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("merchant_dashboard_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = merchantName.ifBlank { "لوحة التاجر" },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isVerified) Surface(shape = RoundedCornerShape(50), color = Color(0xFFE0F2FE), modifier = Modifier.padding(start = 6.dp)) {
                                    Text("✓ موثّق", color = Color(0xFF0369A1), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            Text(
                                text = merchantEmail,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = onEditProfile) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل بياناتي", tint = CalmSagePrimary)
                        }
                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("merchant_logout_btn")
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("خروج", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onOpenAddOfferSheet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("merchant_add_new_offer_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("أضف عرض جديد", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onManageBranches,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (branchesCount > 0) "صفحة متجري وفروعي ($branchesCount)" else "صفحة متجري (الشعار، المنيو، الفروع)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // حالة الحساب: موقوف / جديد (عروضه تُراجع)
        if (isSuspended || !isTrusted) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSuspended) Color(0xFFFDE8E6) else Color(0xFFFFF4E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSuspended)
                            "⛔ حسابك موقوف حالياً، وعروضك مخفية. تواصل مع إدارة لكوفر."
                        else
                            "⏳ حسابك جديد: عروضك تتم مراجعتها قبل ما تظهر على الخريطة. بعد اعتماد أول عرضين بتنشر مباشرة.",
                        fontSize = 13.sp,
                        color = if (isSuspended) Color(0xFFB42318) else Color(0xFF8A5A00),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // ٢. المنيو والطلبات
        if (catalogCard != null) item { catalogCard() }

        // ٣. العروض
        item {
            Text(
                text = "عروضك (${offers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (offers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ما عندك عروض للحين", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اضغط \"أضف عرض جديد\" وانشر أول عرض لك. بيظهر مباشرة على الخريطة.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(offers, key = { it.id }) { offer ->
                MerchantOfferRow(
                    offer = offer,
                    nowMillis = nowMillis,
                    credits = credits,
                    onClick = { onOfferClick(offer) },
                    onEdit = { onEditOffer(offer) },
                    onDelete = { offerPendingDelete = offer }
                )
            }
        }

        // ٤. الرصيد
        if (credits != null) item { WalletCard(credits.balance, credits.items) }

        // ٥. الإحصائيات الحقيقية
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MerchantStatTile("العروض النشطة", "$activeCount", Icons.Default.LocalOffer, Modifier.weight(1f))
                    MerchantStatTile("المشاهدات", "${offers.sumOf { it.viewsCount }}", Icons.Default.Visibility, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MerchantStatTile("طلبوا الاتجاه", "${offers.sumOf { it.directionsCount }}", Icons.Default.Directions, Modifier.weight(1f))
                    MerchantStatTile("تواصلوا واتساب", "${offers.sumOf { it.whatsappCount }}", Icons.Default.Chat, Modifier.weight(1f))
                }
            }
        }

        // التوثيق ✓
        if (!isVerified && verifyEnabled) {
            item {
                Card(
                    onClick = onOpenVerify,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("✓ وثّق متجرك", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF0369A1))
                            Text(
                                verifyStatus ?: "علامة ✓ جنب اسمك، وثقة أكثر، وعروضك تنزل مباشرة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("›", fontSize = 22.sp, color = Color(0xFF0369A1))
                    }
                }
            }
        }

        item { ContactUsButton(unread = unreadReplies, onClick = onOpenContact) }

        // الحساب والخصوصية
        item {
            AccountSettingsSection(onPrivacy = { showPrivacy = true }, onDelete = { confirmDeleteAccount = true }, modifier = Modifier.padding(top = 12.dp))
        }
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })

    if (confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = false },
            title = { Text("حذف الحساب نهائياً؟") },
            text = { Text("بيتم حذف حسابك وكل عروضك (${offers.size}) وإحصائياتها، وما تقدر تسترجعها.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAccount = false
                    onDeleteAccount()
                }) { Text("احذف نهائياً", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAccount = false }) { Text("إلغاء") } }
        )
    }

    offerPendingDelete?.let { offer ->
        AlertDialog(
            onDismissRequest = { offerPendingDelete = null },
            title = { Text("حذف العرض؟") },
            text = { Text("بيختفي \"${offer.offerTitle}\" من الخريطة ومن صفحة العروض. ما تقدر تتراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteOffer(offer.id)
                    offerPendingDelete = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { offerPendingDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MerchantOfferRow(
    offer: RestaurantOffer,
    nowMillis: Long,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    credits: MerchantCredits? = null
) {
    val expired = OfferTime.isExpired(offer, nowMillis)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductMediaViewer(
                mediaUri = offer.mediaUri,
                mediaType = offer.mediaType,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = offer.offerTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (offer.adminNote.isNotBlank()) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFDE8E6)) {
                    Text("↩️ يحتاج تعديل: ${offer.adminNote} — اضغط ✎", color = Color(0xFFB42318), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                } else if (offer.draft) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4E0)) {
                    Text("📝 مسودة — بانتظار الرصيد", color = Color(0xFF8A5A00), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!offer.isApproved || offer.reportsCount >= com.example.ui.MerchantViewModel.AUTO_HIDE_REPORTS) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4E0)) {
                            Text(
                                if (!offer.isApproved) "⏳ بانتظار المراجعة" else "🚩 مخفي للمراجعة",
                                color = Color(0xFF8A5A00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else if (offer.expiresAt == null && !com.example.data.model.OfferPricing.isUpcoming(offer, nowMillis)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE7F6EC)) {
                            Text(
                                "نشط",
                                color = Color(0xFF15803D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        OfferExpiryChip(offer = offer, nowMillis = nowMillis)
                    }
                    if (offer.isFeatured) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4E0)) {
                            Text(
                                "⭐ مميز",
                                color = Color(0xFF8A5A00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                val chips = if (offer.draft) emptyList() else credits?.chips?.invoke(offer).orEmpty()
                if (chips.isNotEmpty()) Text(chips.joinToString("  •  "), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A5A00))
                if (offer.draft && credits != null) androidx.compose.material3.Button(
                    onClick = { credits.onPublishDraft(offer) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) { Text("📢 انشر الحين", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Text(
                    text = "👁 ${offer.viewsCount}   🧭 ${offer.directionsCount}   💬 ${offer.whatsappCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (credits != null && !offer.draft && !expired) androidx.compose.material3.IconButton(onClick = { credits.onBoost(offer) }) {
                Text("🚀", fontSize = 18.sp)
            }
            androidx.compose.material3.IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل العرض", tint = CalmSagePrimary)
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف العرض", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MerchantStatTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = CalmSagePrimary.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** ⚙️ إعدادات الحساب: مطوية افتراضياً، وحذف الحساب داخلها (عشان ما ينضغط بالغلط) */
@Composable
fun AccountSettingsSection(onPrivacy: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("⚙️ إعدادات الحساب", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (open) "▴" else "▾", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { onPrivacy() }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("🔒 سياسة الخصوصية", fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("›", fontSize = 18.sp)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable { onDelete() }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("🗑️ حذف الحساب نهائياً", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)); Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
