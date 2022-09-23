import * as React from 'react';
import {useState}  from 'react';
import {StyleSheet, SafeAreaView, View, Image, ScrollView} from 'react-native';
import { Text } from '../components/Themed';

export default function Settings({route, navigation}) {

  const {param} = route.params;

  return(
    <View style={styles.container}>
      <Text style={styles.buttonText}> {JSON.stringify(param)} </Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'aliceblue',
  },
  buttonContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginVertical: 8,
  },

  image: {
    marginVertical: 24,
    alignItems: 'center',
  },
  buttonText: {
    fontSize: 20,
    color: 'black',
    flex: 1
  },
});