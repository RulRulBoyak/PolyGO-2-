import { StyleSheet, Image, View, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';

export default function HomeScreen() {
  return (
    <ThemedView style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Deep Blue Premium Header */}
        <LinearGradient
          colors={['#0D47A1', '#1E88E5']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={styles.header}
        >
          <SafeAreaView edges={['top']}>
            <View style={styles.navBar}>
              <ThemedText style={styles.brandName}>PolyGo+</ThemedText>
              <TouchableOpacity style={styles.profileBtn}>
                <Ionicons name="person-circle-outline" size={32} color="white" />
              </TouchableOpacity>
            </View>

            <View style={styles.heroContent}>
              <ThemedText style={styles.greeting}>Hi, Amirul!</ThemedText>
              <ThemedText style={styles.subtitle}>
                What's on your PKS list today?
              </ThemedText>
            </View>
          </SafeAreaView>
        </LinearGradient>

        {/* Floating Modern Search Bar */}
        <View style={styles.searchContainer}>
          <TouchableOpacity style={styles.searchBar}>
            <Ionicons name="search" size={20} color="#0D47A1" />
            <ThemedText style={styles.searchText}>
              Search books, food, or electronics...
            </ThemedText>
            <Ionicons name="options-outline" size={20} color="#222" />
          </TouchableOpacity>
        </View>

        {/* Main Content */}
        <View style={styles.content}>
          <ThemedText style={styles.sectionTitle}>Explore Campus</ThemedText>

          {/* Quick Category Mockup */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryList}>
            {['Food', 'Drinks', 'Tech', 'Books', 'Repair', 'Others'].map((cat, i) => (
              <View key={i} style={styles.categoryItem}>
                <View style={styles.categoryCard}>
                  <Ionicons name="grid-outline" size={24} color="#0D47A1" />
                </View>
                <ThemedText style={styles.categoryText}>{cat}</ThemedText>
              </View>
            ))}
          </ScrollView>

          {/* Trust Banner */}
          <View style={styles.banner}>
            <View style={styles.bannerText}>
              <ThemedText style={styles.bannerTitle}>Verified PKS Sellers</ThemedText>
              <ThemedText style={styles.bannerSub}>
                Deal with peace of mind. Every member is verified with their ID.
              </ThemedText>
            </View>
            <Ionicons name="shield-checkmark" size={40} color="#0D47A1" style={styles.bannerIcon} />
          </View>

          <View style={styles.featuredHeader}>
            <ThemedText style={styles.sectionTitle}>Featured Listings</ThemedText>
            <TouchableOpacity>
              <ThemedText style={styles.seeAll}>Browse All</ThemedText>
            </TouchableOpacity>
          </View>

          {/* Empty State Mockup */}
          <View style={styles.emptyContainer}>
            <Ionicons name="file-tray-outline" size={64} color="#C9DEFF" />
            <ThemedText style={styles.emptyTitle}>Nothing here yet</ThemedText>
            <ThemedText style={styles.emptySub}>
              Be the first to list something in the PKS Marketplace!
            </ThemedText>
          </View>
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    height: 260,
    paddingHorizontal: 24,
  },
  navBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 10,
  },
  brandName: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
  },
  profileBtn: {
    padding: 2,
  },
  heroContent: {
    marginTop: 40,
  },
  greeting: {
    color: '#fff',
    fontSize: 34,
    fontWeight: 'bold',
  },
  subtitle: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 15,
    marginTop: 6,
  },
  searchContainer: {
    paddingHorizontal: 24,
    marginTop: -30,
  },
  searchBar: {
    height: 60,
    backgroundColor: '#fff',
    borderRadius: 14,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 6,
  },
  searchText: {
    flex: 1,
    marginLeft: 12,
    color: '#717171',
    fontSize: 15,
  },
  content: {
    paddingTop: 32,
    paddingBottom: 100,
  },
  sectionTitle: {
    fontSize: 19,
    fontWeight: 'bold',
    color: '#222',
    marginLeft: 24,
  },
  categoryList: {
    paddingLeft: 24,
    paddingRight: 12,
    marginTop: 16,
  },
  categoryItem: {
    alignItems: 'center',
    marginRight: 16,
  },
  categoryCard: {
    width: 74,
    height: 74,
    backgroundColor: '#F5F7FA',
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EEEEEE',
  },
  categoryText: {
    marginTop: 10,
    fontSize: 13,
    fontWeight: 'bold',
    color: '#222',
  },
  banner: {
    marginHorizontal: 24,
    marginTop: 32,
    backgroundColor: '#E3F2FD',
    borderRadius: 20,
    padding: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  bannerText: {
    flex: 1,
  },
  bannerTitle: {
    color: '#0D47A1',
    fontSize: 16,
    fontWeight: 'bold',
  },
  bannerSub: {
    color: '#717171',
    fontSize: 12,
    marginTop: 4,
  },
  bannerIcon: {
    marginLeft: 12,
    opacity: 0.8,
  },
  featuredHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginHorizontal: 24,
    marginTop: 40,
  },
  seeAll: {
    color: '#0D47A1',
    fontSize: 14,
    fontWeight: 'bold',
  },
  emptyContainer: {
    alignItems: 'center',
    marginTop: 40,
    paddingHorizontal: 40,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#222',
    marginTop: 16,
  },
  emptySub: {
    fontSize: 14,
    color: '#717171',
    textAlign: 'center',
    marginTop: 8,
  },
});
