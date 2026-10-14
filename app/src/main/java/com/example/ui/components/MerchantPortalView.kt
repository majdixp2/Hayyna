package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * صفحة التاجر الجديدة:
 * - تسجيل دخول التاجر بالبريد الإلكتروني
 * - زر إضافة عرض جديد أو فيديو إعلاني للمطعم المنقول هنا
 * - إحصائيات المطعم وإدارة عروض التاجر
 */
@Composable
fun MerchantPortalView(
    isLoggedIn: Boolean,
    merchantEmail: String?,
    merchantRestaurantName: String,
    merchantOffers: List<RestaurantOffer>,
    onLogin: (email: String, restaurantName: String) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onLogout: () -> Unit,
    onOpenAddOfferSheet: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isLoggedIn) {
        // 1. تسجيل دخول التاجر بالبريد الإلكتروني أو جوجل
        MerchantLoginForm(
            onLogin = onLogin,
            onGoogleSignIn = onGoogleSignIn,
            modifier = modifier
        )
    } else {
        // 2. لوحة تحكم التاجر بعد تسجيل الدخول وبها زر إضافة عرض
        MerchantDashboard(
            merchantEmail = merchantEmail ?: "",
            restaurantName = merchantRestaurantName,
            offers = merchantOffers,
            onOpenAddOfferSheet = onOpenAddOfferSheet,
            onLogout = onLogout,
            onOfferClick = onOfferClick,
            onDeleteOffer = onDeleteOffer,
            modifier = modifier
        )
    }
}

@Composable
fun MerchantLoginForm(
    onLogin: (email: String, restaurantName: String) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("chef.saad@restaurant.com") }
    var restaurantName by remember { mutableStateOf("مطعم ومقهى ناي") }
    var password by remember { mutableStateOf("123456") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("merchant_login_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = CircleShape,
                color = CalmSagePrimary.copy(alpha = 0.12f),
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = CalmSagePrimary,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "بوابة تسجيل دخول التاجر 🏪",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = CalmSagePrimary
            )

            Text(
                text = "سجّل دخولك بالبريد الإلكتروني لإضافة عروضك الترويجية بالصور والفيديو على الخريطة",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "بيانات حساب التاجر:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Email input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("البريد الإلكتروني للتاجر") },
                        placeholder = { Text("مثال: restaurant@owner.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = CalmSagePrimary)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merchant_email_input")
                    )

                    // Restaurant name input
                    OutlinedTextField(
                        value = restaurantName,
                        onValueChange = { restaurantName = it },
                        label = { Text("اسم المطعم / المنشأة") },
                        placeholder = { Text("مثال: شاورما كينج، مطعم ناي") },
                        leadingIcon = {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = CalmSagePrimary)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merchant_restaurant_name_input")
                    )

                    // Password input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور أو رمز الدخول") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CalmSagePrimary)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merchant_password_input")
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    // Google Sign-In button
                    OutlinedButton(
                        onClick = { onGoogleSignIn() },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, CalmSagePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("merchant_google_sign_in_btn")
                    ) {
                        Text(
                            text = "تسجيل الدخول السريع بحساب Google 🚀",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CalmSagePrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("  أو بالبريد الإلكتروني  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Login Button
                    Button(
                        onClick = {
                            if (email.isBlank() || !email.contains("@")) {
                                errorMessage = "يرجى كتابة بريد إلكتروني صحيح للتاجر"
                            } else {
                                onLogin(email, restaurantName)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("merchant_login_submit_btn")
                    ) {
                        Text(
                            text = "تسجيل الدخول إلى لوحة التاجر 🔐",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }


        // Quick login presets for easy testing
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚡ حسابات تجريبية للدخول السريع:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onLogin("saad@nai-cafe.sa", "مطعم ومقهى ناي")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("مطعم ناي", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onLogin("king@shawarma.sa", "شاورما كينج")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("شاورما كينج", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onLogin("burger@lounge.sa", "برجر لاونج")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("برجر لاونج", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MerchantDashboard(
    merchantEmail: String,
    restaurantName: String,
    offers: List<RestaurantOffer>,
    onOpenAddOfferSheet: () -> Unit,
    onLogout: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("merchant_dashboard_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Merchant Header Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CalmSagePrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = CalmSagePrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = restaurantName.ifBlank { "لوحة التاجر" },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "البريد: $merchantEmail",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Logout button
                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("merchant_logout_btn")
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "خروج", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("خروج", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // THE PRIMARY ACTION MOVED HERE: زر إضافة عرض جديد أو فيديو إعلاني للمطعم
                    Button(
                        onClick = onOpenAddOfferSheet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("merchant_add_new_offer_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ إضافة عرض جديد أو فيديو إعلاني للمطعم 🎬",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MerchantStatPill(
                    title = "العروض النشطة",
                    value = "${offers.size}",
                    icon = Icons.Default.Restaurant,
                    modifier = Modifier.weight(1f)
                )
                MerchantStatPill(
                    title = "إجمالي المشاهدات",
                    value = "${offers.sumOf { it.viewsCount }}",
                    icon = Icons.Default.Visibility,
                    modifier = Modifier.weight(1f)
                )
                MerchantStatPill(
                    title = "تقييم المطعم",
                    value = "4.9 ⭐",
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Offers Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "عروض مطعمك المنشورة (${offers.size}):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "ظاهرة للعملاء على الخريطة ✅",
                    fontSize = 11.sp,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (offers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🍽️ لا توجد عروض مضافة بعد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اضغط على الزر الأخضر أعلاه لإضافة عرضك الترويجي وصور وجباتك أو فيديو إعلاني للمطعم",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(offers, key = { it.id }) { offer ->
                RestaurantOfferCard(
                    offer = offer,
                    onOfferClick = { onOfferClick(offer) },
                    showAdminControls = false
                )
            }
        }
    }
}

@Composable
fun MerchantStatPill(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CalmSagePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = CalmSagePrimary
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
